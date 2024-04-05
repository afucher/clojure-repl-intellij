(ns com.github.clojure-repl.intellij.nrepl
  (:refer-clojure :exclude [eval load-file])
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [nrepl.core :as nrepl.core])
  (:import
   [com.intellij.openapi.project Project]
   [nrepl.transport FnTransport]))

(set! *warn-on-reflection* true)

(defn ^:private send-message [project message]
  (with-open [conn ^FnTransport (nrepl.core/connect
                                 :host (db/get-in project [:current-nrepl :nrepl-host])
                                 :port (db/get-in project [:current-nrepl :nrepl-port]))]
    ;; TODO Improve this timeout, what will happen for tests/evals
    ;; taking more than this timeout? should we really fail?
    (-> (nrepl.core/client conn 60000)
        (nrepl.core/message message)
        doall
        nrepl.core/combine-responses)))

(defn eval [& {:keys [^Project project ns code]
               :or {ns (or (db/get-in project [:current-nrepl :ns]) "user")}}]
  (let [{:keys [ns] :as response} (send-message project {:op "eval" :code code :ns ns :session (db/get-in project [:current-nrepl :session-id])})]
    (when ns
      (db/assoc-in! project [:current-nrepl :ns] ns))
    (doseq [fn (db/get-in project [:on-repl-evaluated-fns])]
      (fn project response))
    response))

(defn clone-session [^Project project]
  (db/assoc-in! project [:current-nrepl :session-id] (:new-session (send-message project {:op "clone"}))))

(defn load-file [project ^java.io.File file]
  (let [result (send-message project {:op "load-file"
                                      :session (db/get-in project [:current-nrepl :session-id])
                                      :file (slurp file)
                                      :file-path (.getCanonicalPath file)
                                      :file-name (.getName file)})]
    (doseq [fn (db/get-in project [:on-repl-file-loaded-fns])]
      (fn file))
    result))

(defn describe [^Project project]
  (send-message project {:op "describe"}))

(defn sym-info [^Project project ns sym]
  (send-message project {:op "info" :ns ns :sym sym}))

(defn run-tests [^Project project {:keys [ns tests on-ns-not-found on-out on-err on-succeeded on-failed]}]
  (let [{:keys [summary results status out err] :as response} (send-message project {:op "test" :ns ns :tests (when (seq tests) tests)})]
    (when (some #(= % "namespace-not-found") status)
      (on-ns-not-found ns))
    (when out (on-out out))
    (when err (on-err err))
    (when results
      (if (zero? (+ (:error summary) (:fail summary)))
        (on-succeeded response)
        (on-failed response)))))
