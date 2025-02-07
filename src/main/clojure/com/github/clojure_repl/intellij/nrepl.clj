(ns com.github.clojure-repl.intellij.nrepl
    (:refer-clojure :exclude [eval load-file])
    (:require
     [clojure.core.async :as async]
     [clojure.string :as string]
     [com.github.clojure-repl.intellij.config :as config]
     [com.github.clojure-repl.intellij.db :as db]
     [com.github.ericdallo.clj4intellij.logger :as logger]
     [nrepl.core :as nrepl.core]
     [nrepl.transport :as transport])
    (:import
     [com.intellij.openapi.editor Editor]
     [com.intellij.openapi.project Project]
     [com.intellij.openapi.vfs VirtualFile]))

(set! *warn-on-reflection* true)

(defprotocol REPLClient
             (start! [this])
             (log! [this level arg1] [this level arg1 arg2])
             (send-message! [this msg]))

(defrecord ^:private NREPLClient [host port join sent-messages* received-messages* conn* on-receive-async-message]
           REPLClient
           (start! [this]
                   (log! this :info "connecting...")
                   (reset! conn* (nrepl.core/connect :host host :port port))
                   (async/thread
                    (loop []
                          (if-let [{:keys [id status] :as resp} (transport/recv @conn*)]
                                  (do
                                   (when-not (:changed-namespaces resp)
                                             (log! this :info "received message:" resp)
                                             (if-let [sent-request (get @sent-messages* id)]
                                                     (do
                                                      (swap! received-messages* update id conj resp)
                                                      (when (and status (contains? (set status) "done"))
                                                            (let [responses (get @received-messages* id)]
                                                                 (swap! sent-messages* dissoc id)
                                                                 (swap! received-messages* dissoc id)
                                                                 (deliver sent-request (nrepl.core/combine-responses responses)))))
                                                     (on-receive-async-message resp)))
                                   (recur))
                                  (deliver join :done))))
                   (log! this :info "connected")
                   join)
           (send-message! [this msg]
                          (let [id (str (random-uuid))
                                p (promise)
                                msg (assoc msg :id id)]
                               (log! this :info "sending message:" msg)
                               (swap! sent-messages* assoc id p)
                               (transport/send @conn* msg)
                               p))
           (log! [this msg params]
                 (log! this :info msg params))
           (log! [_this _color msg params]
                 (when (config/nrepl-debug?)
                       (logger/info "[NREPLClient] " (string/join " " [msg params])))))

(defn ^:private nrepl-client [^Project project on-receive-async-message]
      (map->NREPLClient {:host (db/get-in project [:current-nrepl :nrepl-host])
                         :port (db/get-in project [:current-nrepl :nrepl-port])
                         :join (promise)
                         :on-receive-async-message on-receive-async-message
                         :conn* (atom nil)
                         :sent-messages* (atom {})
                         :received-messages* (atom {})}))

(defn start-client! [& {:keys [project on-receive-async-message]}]
      (let [client (nrepl-client project on-receive-async-message)]
           (db/assoc-in! project [:current-nrepl :client] client)
           (start! client)))

(defn ^:private send-msg [project message]
      (let [client (db/get-in project [:current-nrepl :client])]
           (send-message! client message)))

(defn eval [& {:keys [^Project project ns code]}]
      (let [ns (or ns (db/get-in project [:current-nrepl :ns]) "user")
            {:keys [ns] :as response} @(send-msg project {:op "eval" :code code :ns ns :session (db/get-in project [:current-nrepl :session-id])})]
           (when ns
                 (db/assoc-in! project [:current-nrepl :ns] ns))
           (doseq [fn (db/get-in project [:on-repl-evaluated-fns])]
                  (fn project response))
           response))

(defn clone-session [^Project project]
      (db/assoc-in! project [:current-nrepl :session-id] (:new-session @(send-msg project {:op "clone"}))))

(defn load-file [project ^Editor editor ^VirtualFile virtual-file]
      (let [response @(send-msg project {:op "load-file"
                                         :session (db/get-in project [:current-nrepl :session-id])
                                         :file (.getText (.getDocument editor))
                                         :file-path  (some-> virtual-file .getPath)
                                         :file-name (some-> virtual-file .getName)})]
           (doseq [fn (db/get-in project [:on-repl-evaluated-fns])]
                  (fn project response))
           response))

(defn describe [^Project project]
      @(send-msg project {:op "describe"}))

(defn out-subscribe [^Project project]
      @(send-msg project {:op "out-subscribe" :session (db/get-in project [:current-nrepl :session-id])}))

(defn sym-info [^Project project ns sym]
      @(send-msg project {:op "info" :ns ns :sym sym :session (db/get-in project [:current-nrepl :session-id])}))

(defn run-tests [^Project project {:keys [ns tests on-ns-not-found on-out on-err on-succeeded on-failed]}]
      (let [{:keys [summary results status out err] :as response}
            @(send-msg project {:op "test"
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

(defn refresh-all [^Project project]
      @(send-msg project {:op "refresh-all"}))

(defn refresh [^Project project]
      @(send-msg project {:op "refresh"}))

(defn interrupt [^Project project]
      @(send-msg project {:op "interrupt" :session (db/get-in project [:current-nrepl :session-id])}))

(defn evaluating? [^Project project]
      (when-let [client (db/get-in project [:current-nrepl :client])]
        (seq @(:sent-messages* client))))
