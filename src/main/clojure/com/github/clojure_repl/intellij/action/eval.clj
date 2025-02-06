(ns com.github.clojure-repl.intellij.action.eval
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.clojure-repl.intellij.ui.hint :as ui.hint]
   [com.github.clojure-repl.intellij.ui.inlay-hint :as ui.inlay-hint]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.tasks :as tasks]
   [com.github.ericdallo.clj4intellij.util :as util]
   [rewrite-clj.zip :as z])
  (:import
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.vfs VirtualFile]))

(set! *warn-on-reflection* true)

(defn ^:private eval-action [^AnActionEvent event loading-msg eval-fn success-msg-fn {:keys [inlay-hint-feedback?]}]
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
                   (if inlay-hint-feedback?
                     (ui.inlay-hint/show-code (success-msg-fn response) editor)
                     (ui.hint/show-repl-info :message (success-msg-fn response) :editor editor))))}))))
        (ui.hint/show-error :message "No REPL connected" :editor editor)))))

(defn load-file-action [^AnActionEvent event]
  (when-let [virtual-file ^VirtualFile (.getData event CommonDataKeys/VIRTUAL_FILE)]
    (eval-action
     event
     "REPL: Loading file"
     (fn [^Editor editor]
       (nrepl/load-file (.getProject editor) editor virtual-file))
     (fn [_response]
       (str "Loaded file " (.getPath ^VirtualFile virtual-file)))
     {})))

(defn eval-last-sexpr-action [^AnActionEvent event]
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (let [[row col] (util/editor->cursor-position editor)]
      (eval-action
       event
       "REPL: Evaluating"
       (fn [^Editor editor]
         (let [text (.getText (.getDocument editor))
               root-zloc (z/of-string text)
               zloc (parser/find-form-at-pos root-zloc (inc row) col)
               code (z/string zloc)
               ns (some-> (parser/find-namespace root-zloc) z/string parser/remove-metadata)]
           (nrepl/eval {:project (.getProject editor) :code code :ns ns})))
       (fn [response]
         (string/join "\n" (:value response)))
       {:inlay-hint-feedback? true}))))

(defn eval-defun-action [^AnActionEvent event]
  (eval-action
   event
   "REPL: Evaluating"
   (fn [^Editor editor]
     (let [[row col] (util/editor->cursor-position editor)
           text (.getText (.getDocument editor))
           root-zloc (z/of-string text)
           zloc (-> (parser/find-form-at-pos root-zloc (inc row) col)
                    parser/to-top)
           code (z/string zloc)
           ns (some-> (parser/find-namespace root-zloc) z/string parser/remove-metadata)]
       (nrepl/eval {:project (.getProject editor) :code code :ns ns})))
   (fn [response]
     (string/join "\n" (:value response)))
   {:inlay-hint-feedback? true}))

(defn clear-repl-output-action [^AnActionEvent event]
  (let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)
        project ^Project (or (.getData event CommonDataKeys/PROJECT)
                             (.getProject editor))]
    (ui.repl/clear-repl project (db/get-in project [:console :ui]))))

(defn history-up-action [^AnActionEvent event]
  (let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)
        project ^Project (or (.getData event CommonDataKeys/PROJECT)
                             (.getProject editor))]
    (ui.repl/history-up project)))

(defn history-down-action [^AnActionEvent event]
  (let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)
        project ^Project (or (.getData event CommonDataKeys/PROJECT)
                             (.getProject editor))]
    (ui.repl/history-down project)))

(defn switch-ns-action [^AnActionEvent event]
  (eval-action
   event
   "REPL: Switching ns"
   (fn [^Editor editor]
     (let [text (.getText (.getDocument editor))
           root-zloc (z/of-string text)
           zloc (parser/find-namespace root-zloc)
           namespace (parser/remove-metadata (z/string zloc))]
       (nrepl/eval {:project (.getProject editor) :code (format "(in-ns '%s)" namespace) :ns namespace})))
   (fn [response]
     (string/join "\n" (:value response)))
   {}))

(defn refresh-all-action [^AnActionEvent event]
  (eval-action
   event
   "REPL: Refreshing all ns"
   (fn [^Editor editor]
     (nrepl/refresh-all (.getProject editor)))
   (fn [response]
     (if (contains? (:status response) "ok")
       "Refreshed sucessfully"
       (str "Refresh failed:\n" (:error response))))
   {}))

(defn refresh-changed-action [^AnActionEvent event]
  (eval-action
   event
   "REPL: Refreshing changed ns"
   (fn [^Editor editor]
     (nrepl/refresh (.getProject editor)))
   (fn [response]
     (if (contains? (:status response) "ok")
       "Refreshed sucessfully"
       (str "Refresh failed:\n" (:error response))))
   {}))
