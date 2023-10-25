(ns com.github.clojure-repl.intellij.nrepl
  (:refer-clojure :exclude [eval load-file])
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [nrepl.core :as nrepl.core])
  (:import
   [nrepl.transport FnTransport]))

(defn ^:private send-message [message]
  (with-open [conn ^FnTransport (nrepl.core/connect
                                 :host (-> @db/db* :settings :nrepl-host)
                                 :port (-> @db/db* :settings :nrepl-port))]
    (-> (nrepl.core/client conn 1000)
        (nrepl.core/message message)
        doall
        first)))

(defn eval [& {:keys [code]}]
  (let [{:keys [ns] :as response} (send-message {:op "eval" :code code :session (-> @db/db* :current-nrepl :session-id)})]
    (when ns
      (swap! db/db* assoc-in [:current-nrepl :ns] ns))
    response))

(defn clone-session []
  (swap! db/db* assoc-in [:current-nrepl :session-id] (:new-session (send-message {:op "clone"}))))

(defn list-sessions []
  (send-message {:op "ls-sessions"}))

(defn load-file [project file]
  (send-message {:op "load-file" :file (slurp file)})
  (doseq [fns (:on-repl-file-loaded-fns @db/db*)]
    (when (= (:project fns) project)
      ((:fn fns) file))))

(defn describe []
  (send-message {:op "describe"}))
