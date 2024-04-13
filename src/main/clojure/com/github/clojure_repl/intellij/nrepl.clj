(ns com.github.clojure-repl.intellij.nrepl
  (:refer-clojure :exclude [eval load-file])
  (:require
   [com.github.clojure-repl.intellij.config :as config]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [nrepl.core :as nrepl.core])
  (:import
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.vfs VirtualFile]
   [nrepl.transport FnTransport]))

(set! *warn-on-reflection* true)

(defn ^:private send-message [project message]
  (when (config/nrepl-debug?)
    (logger/info "Requesting:" message))
  (let [response (with-open [conn ^FnTransport (nrepl.core/connect
                                                :host (db/get-in project [:current-nrepl :nrepl-host])
                                                :port (db/get-in project [:current-nrepl :nrepl-port]))]
                   ;; TODO Improve this timeout, what will happen for tests/evals
                   ;; taking more than this timeout? should we really fail?
                   (-> (nrepl.core/client conn 60000)
                       (nrepl.core/message message)
                       doall
                       nrepl.core/combine-responses))]
    (when (config/nrepl-debug?)
      (logger/info "Responded:" response))
    response))

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

(defn load-file [project ^Editor editor ^VirtualFile virtual-file]
  (let [response (send-message project {:op "load-file"
                                        :session (db/get-in project [:current-nrepl :session-id])
                                        :file (.getText (.getDocument editor))
                                        :file-path  (some-> virtual-file .getPath)
                                        :file-name (some-> virtual-file .getName)})]
    (doseq [fn (db/get-in project [:on-repl-evaluated-fns])]
      (fn project response))
    response))

(defn describe [^Project project]
  (send-message project {:op "describe"}))

(defn sym-info [^Project project ns sym]
  (send-message project {:op "info" :ns ns :sym sym :session (db/get-in project [:current-nrepl :session-id])}))

(defn run-tests [^Project project {:keys [ns tests on-ns-not-found on-out on-err on-succeeded on-failed]}]
  (let [{:keys [summary results status out err] :as response}
        (send-message project {:op "test"
                               :load? (str (boolean ns))
                               :session (db/get-in project [:current-nrepl :session-id])
                               :ns ns
                               :tests (when (seq tests) tests)
                               :fail-fast  "true"})]
    (when (some #(= % "namespace-not-found") status)
      (on-ns-not-found ns))
    (when out (on-out out))
    (when err (on-err err))
    (when results
      (if (zero? (+ (:error summary) (:fail summary)))
        (on-succeeded response)
        (on-failed response)))))
