(ns com.github.clojure-repl.intellij.action.eval
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.actions :as actions]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.editor :as editor]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.clojure-repl.intellij.ui.hint :as ui.hint]
   [com.github.clojure-repl.intellij.ui.inlay-hint :as ui.inlay-hint]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.action :as action]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.github.ericdallo.clj4intellij.tasks :as tasks]
   [com.github.ericdallo.clj4intellij.util :as util]
   [com.rpl.proxy-plus :refer [proxy+]]
   [rewrite-clj.zip :as z])
  (:import
   [com.github.clojure_repl.intellij Icons]
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor SelectionModel]
   [com.intellij.openapi.project DumbAwareAction Project]
   [com.intellij.openapi.vfs VirtualFile]
   [javax.swing Icon]))

(set! *warn-on-reflection* true)

(defn ^:private send-result-to-repl [^AnActionEvent event text prefix?]
  (ui.repl/append-output
   (actions/action-event->project event)
   (str "\n" (if prefix? "=> " "") text)))

(defn ^:private eval-action
  [& {:keys [^AnActionEvent event loading-msg eval-fn success-msg-fn post-success-fn inlay-hint-feedback?]
      :or {post-success-fn identity}}]
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (let [project (.getProject editor)]
      (if (db/get-in project [:current-nrepl :session-id])
        (tasks/run-background-task!
         project
         loading-msg
         (fn [_indicator]
           (let [{:keys [status err] :as response} (eval-fn editor)]
             (app-manager/invoke-later!
              {:invoke-fn
               (fn []
                 (cond
                   (and (contains? status "eval-error") err)
                   (ui.hint/show-repl-error :message err :editor editor)

                   (contains? status "namespace-not-found")
                   (ui.hint/show-error {:message (str "Namespace not found: " (:ns response)) :editor editor})

                   :else
                   (do
                     (if inlay-hint-feedback?
                       (ui.inlay-hint/show-code (success-msg-fn response) editor)
                       (ui.hint/show-repl-info :message (success-msg-fn response) :editor editor))
                     (post-success-fn response))))}))))
        (ui.hint/show-error :message "No REPL connected" :editor editor)))))

(defn load-file-action [^AnActionEvent event]
  (when-let [virtual-file ^VirtualFile (.getData event CommonDataKeys/VIRTUAL_FILE)]
    (let [msg (str "Loaded file " (.getPath ^VirtualFile virtual-file))]
      (eval-action
       :event event
       :loading-msg "REPL: Loading file"
       :eval-fn (fn [^Editor editor]
                  (nrepl/load-file (.getProject editor) editor virtual-file))
       :success-msg-fn (fn [_response] msg)
       :post-success-fn (fn [_response]
                          (send-result-to-repl event (str ";; " msg) false))))))

(defn ^:private current-var [^Editor editor]
  (let [[row col] (util/editor->cursor-position editor)
        text (.getText (.getDocument editor))
        root-zloc (z/of-string text)
        current-var (some-> (parser/find-var-at-pos root-zloc (inc row) col) z/string)]
    current-var))

(defn custom-action [^AnActionEvent event code-snippet]
  (let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)
        selection-model ^SelectionModel (.getSelectionModel editor)
        selection (or (.getSelectedText selection-model)
                      "")
        current-file-ns (some-> (editor/ns-form editor) parser/find-namespace z/string parser/remove-metadata)
        current-var (current-var editor)
        fqn-current-var (str current-file-ns "/" current-var)]
    (logger/info "Custom action triggered")
    (logger/info selection " " current-file-ns " " fqn-current-var)
    (eval-action
     :event event
     :loading-msg "REPL: Evaluating"
     :eval-fn (fn [^Editor editor]
                (let [code (-> code-snippet
                               (string/replace "~current-var" fqn-current-var)
                               (string/replace "~selection" selection))]
                  (nrepl/eval-from-editor {:editor editor :code code})))
     :success-msg-fn (fn [response]
                       (string/join "\n" (:value response)))
     :post-success-fn (fn [response]
                        (send-result-to-repl event (string/join "\n" (:value response)) true))
     :inlay-hint-feedback? true)))


(defn register-custom-code-actions
  ([eval-code-actions]
   (register-custom-code-actions eval-code-actions nil))
  ([eval-code-actions ^Project project]
   (logger/info "Registering custom actions..." eval-code-actions project)
   (->> eval-code-actions
        (mapv (fn [action]
                (logger/info "Registering custom action..." action)
                (let [id (str "ClojureREPL.Custom." (:name action))
                      title ^String (:name action)
                      description ^String (:name action)
                      icon ^Icon Icons/CLOJURE_REPL]
                  (action/register-action!
                   :id id
                   :action
                   (proxy+
                    [title description icon]
                    DumbAwareAction
                    (update
                     [_ ^AnActionEvent event]
                     (when project
                       (let [action-project (actions/action-event->project event)]
                         (.setEnabled (.getPresentation event) (boolean (= project action-project))))))
                    (actionPerformed
                     [_ event]
                     (custom-action event (:code action)))))))))))


(defn eval-last-sexpr-action [^AnActionEvent event]
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (let [[row col] (util/editor->cursor-position editor)]
      (eval-action
       :event event
       :loading-msg "REPL: Evaluating"
       :eval-fn (fn [^Editor editor]
                  (let [text (.getText (.getDocument editor))
                        root-zloc (z/of-string text)
                        zloc (parser/find-form-at-pos root-zloc (inc row) col)
                        special-form? (contains? #{:quote :syntax-quote :var :reader-macro} (-> zloc z/up z/tag))
                        code (if special-form?
                               (-> zloc z/up z/string)
                               (z/string zloc))]
                    (nrepl/eval-from-editor {:editor editor :code code})))
       :success-msg-fn (fn [response]
                         (string/join "\n" (:value response)))
       :post-success-fn (fn [response]
                          (send-result-to-repl event (string/join "\n" (:value response)) true))
       :inlay-hint-feedback? true))))

(defn interrupt [^AnActionEvent event]
  (-> event
      actions/action-event->project
      nrepl/interrupt))

(defn eval-defun-action [^AnActionEvent event]
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (let [[row col] (util/editor->cursor-position editor)]
      (eval-action
       :event event
       :loading-msg "REPL: Evaluating"
       :eval-fn (fn [^Editor editor]
                  (let [text (.getText (.getDocument editor))
                        root-zloc (z/of-string text)
                        zloc (-> (parser/find-form-at-pos root-zloc (inc row) col)
                                 parser/to-top)
                        code (z/string zloc)]
                    (nrepl/eval-from-editor {:editor editor :code code})))
       :success-msg-fn (fn [response]
                         (string/join "\n" (:value response)))
       :post-success-fn (fn [response]
                          (send-result-to-repl event (string/join "\n" (:value response)) true))
       :inlay-hint-feedback? true))))

(defn clear-repl-output-action [^AnActionEvent event]
  (let [project (actions/action-event->project event)]
    (ui.repl/clear-repl project)))

(defn history-up-action [^AnActionEvent event]
  (-> event
      actions/action-event->project
      ui.repl/history-up))

(defn history-down-action [^AnActionEvent event]
  (-> event
      actions/action-event->project
      ui.repl/history-down))

(defn switch-ns-action [^AnActionEvent event]
  (eval-action
   :event event
   :loading-msg "REPL: Switching ns"
   :eval-fn (fn [^Editor editor]
              (let [text (.getText (.getDocument editor))
                    root-zloc (z/of-string text)
                    zloc (parser/find-namespace root-zloc)
                    namespace (parser/remove-metadata (z/string zloc))]
                (nrepl/switch-ns {:project (.getProject editor) :ns namespace})))
   :success-msg-fn (fn [response]
                     (string/join "\n" (:value response)))
   :post-success-fn (fn [_response]
                      (ui.repl/clear-input (actions/action-event->project event)))))

(defn refresh-all-action [^AnActionEvent event]
  (let [msg "Refreshed all sucessfully"]
    (eval-action
     :event event
     :loading-msg "REPL: Refreshing all ns"
     :eval-fn (fn [^Editor editor]
                (nrepl/refresh-all (.getProject editor)))
     :success-msg-fn (fn [response]
                       (if (contains? (:status response) "ok")
                         msg
                         (str "Refresh failed:\n" (:error response))))
     :post-success-fn (fn [_response]
                        (send-result-to-repl event (str ";; " msg) false)))))

(defn refresh-changed-action [^AnActionEvent event]
  (let [msg "Refreshed sucessfully"]
    (eval-action
     :event event
     :loading-msg "REPL: Refreshing changed ns"
     :eval-fn (fn [^Editor editor]
                (nrepl/refresh (.getProject editor)))
     :success-msg-fn (fn [response]
                       (if (contains? (:status response) "ok")
                         msg
                         (str "Refresh failed:\n" (:error response))))
     :post-success-fn (fn [_response]
                        (send-result-to-repl event (str ";; " msg) false)))))
