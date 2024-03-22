(ns com.github.clojure-repl.intellij.nrepl
  (:refer-clojure :exclude [eval load-file])
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [nrepl.core :as nrepl.core])
  (:import
   [com.intellij.openapi.project Project]
   [nrepl.transport FnTransport]))

(set! *warn-on-reflection* true)

(defn ^:private send-message [message]
  (with-open [conn ^FnTransport (nrepl.core/connect
                                 :host (-> @db/db* :settings :nrepl-host)
                                 :port (-> @db/db* :settings :nrepl-port))]
    ;; TODO Improve this timeout, what will happen for tests/evals
    ;; taking more than this timeout? should we really fail?
    (-> (nrepl.core/client conn 60000)
        (nrepl.core/message message)
        doall
        first)))

(defn eval [& {:keys [^Project project code]}]
  (let [{:keys [ns out] :as response} (send-message {:op "eval" :code code :session (-> @db/db* :current-nrepl :session-id)})]
    (when ns
      (swap! db/db* assoc-in [:current-nrepl :ns] ns))
    (when out
      ;; TODO print `out` to current console. Depends on listeners for that.
      out)
    (doseq [fn (:on-repl-evaluated-fns @db/db*)]
      (when (= (:project fn) (.getName project))
        ((:fn fn) response)))
    response))

(defn clone-session []
  (swap! db/db* assoc-in [:current-nrepl :session-id] (:new-session (send-message {:op "clone"}))))

(defn load-file [project ^java.io.File file]
  (let [result (send-message {:op "load-file"
                              :file (slurp file)
                              :file-path (.getCanonicalPath file)
                              :file-name (.getName file)})]
    (doseq [fns (:on-repl-file-loaded-fns @db/db*)]
      (when (= (:project fns) project)
        ((:fn fns) file)))
    result))

(defn describe []
  (send-message {:op "describe"}))

(defn run-tests [ns {:keys [on-ns-not-found on-out on-err on-succeeded on-failed]}]
  (let [{:keys [summary results status out err] :as response} (send-message {:op "test" :ns ns})]
    (when (some #(= % "namespace-not-found") status)
      (on-ns-not-found ns))
    (when out (on-out out))
    (when err (on-err err))
    (when results
      ;; TODO save last result and summary
      ;; TODO highlight errors on editor
      (if (zero? (+ (:error summary) (:fail summary)))
        (on-succeeded response)
        (on-failed response)))))
