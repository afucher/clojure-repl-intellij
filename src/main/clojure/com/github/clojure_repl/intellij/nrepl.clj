(ns com.github.clojure-repl.intellij.nrepl
  (:refer-clojure :exclude [eval load-file])
  (:require
   [clojure.core.async :as async]
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.config :as config]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.editor :as editor]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [nrepl.core :as nrepl.core]
   [nrepl.transport :as transport]
   [rewrite-clj.zip :as z])
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
                  (on-receive-async-message resp)
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

(defn ^:private ns-form-changed [project url ns-form]
  (not (= ns-form
          (db/get-in project [:file->ns url :form]))))
(defn ^:private is-ns-form [form]
  (parser/find-namespace (z/of-string form)))

(defn ^:private eval [^Project project ns code]
  (let [session (db/get-in project [:current-nrepl :session-id])
        response @(send-msg project {:op "eval" :code code :ns ns :session session})]
    #_(doseq [fn (db/get-in project [:on-repl-evaluated-fns])]
        (fn project response))
    response))

(defn prep-env-for-eval
  "When evaluating a form for the first time, 
   need to evaluate the ns form first to avoid namespace-not-found error.
   Also evaluate the ns form when it has changed to keep the environment up-to-date."
  [^Editor editor form]
  (when-let [current-ns-form (editor/ns-form editor)]
    (let [url (editor/editor->url editor)
          project (.getProject editor)
          str-current-ns-form (z/string current-ns-form)
          ns (-> current-ns-form parser/find-namespace z/string parser/remove-metadata)]
      (when (and (not (is-ns-form form))
                 (ns-form-changed project url str-current-ns-form))
        (eval project "user" str-current-ns-form)
        (when ns
          (db/assoc-in! project [:file->ns url]
                        {:form str-current-ns-form
                         :ns ns}))))))

(defn ^:private cur-ns
  "Returns current ns to evaluate code in.
   If there is no ns in cache, it returns the ns from the file.
   If the ns form is not found in the editor, it default to 'user'."
  [^Editor editor]
  (let [project (.getProject editor)
        url (editor/editor->url editor)
        cur-ns (some-> (editor/ns-form editor) parser/find-namespace z/string parser/remove-metadata)]
    (or (db/get-in project [:file->ns url :ns])
        cur-ns
        "user")))

(defn eval-from-editor
  "When evaluating code related to a file (editor) we need to:
   - prepare the environment by evaluating the ns form
   - evaluate the code
   - update the current ns in the project if it has changed"
  [& {:keys [^Editor editor ns code]}]
  (prep-env-for-eval editor code)
  (let [project (.getProject editor)
        url (editor/editor->url editor)
        ns (or ns
               (cur-ns editor))
        {:keys [ns] :as response} (eval project ns code)]
    (when ns
      (db/assoc-in! project [:file->ns url :ns] ns))
    response))

(defn eval-from-repl
  "When evaluating from repl window we do not have editor associate"
  [& {:keys [^Project project ns code]}]
  (let [ns (or ns
               (db/get-in project [:current-nrepl :ns]))]
    (eval project ns code)))

(defn switch-ns [{:keys [project ns]}]
  (let [code (format "(in-ns '%s)" ns)
        response @(send-msg project {:op "eval" :code code :ns ns :session (db/get-in project [:current-nrepl :session-id])})]
    (when (and ns
               (not= ns (db/get-in project [:current-nrepl :ns])))
      (db/assoc-in! project [:current-nrepl :ns] ns)
      (doseq [fn (db/get-in project [:on-ns-changed-fns])]
        (fn project response)))
    response))

(defn clone-session [^Project project]
  (let [msg {:op "clone"
             :client-name "clojure-repl-intellij"
             :client-version (config/plugin-version)}]
    (db/assoc-in! project [:current-nrepl :session-id] (:new-session @(send-msg project msg)))))

(defn load-file [project ^Editor editor ^VirtualFile virtual-file]
  (let [response @(send-msg project {:op "load-file"
                                     :session (db/get-in project [:current-nrepl :session-id])
                                     :file (.getText (.getDocument editor))
                                     :file-path  (some-> virtual-file .getPath)
                                     :file-name (some-> virtual-file .getName)})]
    #_(doseq [fn (db/get-in project [:on-repl-evaluated-fns])]
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

(defn test-stacktrace [^Project project ns var]
  @(send-msg project {:op "test-stacktrace"
                      :session (db/get-in project [:current-nrepl :session-id])
                      :ns ns
                      :index 0
                      :var var}))

(defn refresh-all [^Project project]
  @(send-msg project {:op "refresh-all"}))

(defn refresh [^Project project]
  @(send-msg project {:op "refresh"}))

(defn interrupt [^Project project]
  @(send-msg project {:op "interrupt" :session (db/get-in project [:current-nrepl :session-id])}))

(defn evaluating? [^Project project]
  (when-let [client (db/get-in project [:current-nrepl :client])]
    (seq @(:sent-messages* client))))

(defn active-client? [^Project project]
  (db/get-in project [:current-nrepl :client]))
