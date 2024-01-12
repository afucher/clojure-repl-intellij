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
    (-> (nrepl.core/client conn 1000)
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
