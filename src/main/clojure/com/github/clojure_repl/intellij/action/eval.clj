(ns com.github.clojure-repl.intellij.action.eval
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.clojure-repl.intellij.ui.hint :as ui.hint]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.tasks :as tasks]
   [com.github.ericdallo.clj4intellij.util :as util]
   [rewrite-clj.zip :as z])
  (:import
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.vfs VirtualFile]))

(set! *warn-on-reflection* true)

(defn ^:private eval-action [^AnActionEvent event loading-msg eval-fn success-msg-fn]
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (let [project (.getProject editor)]
      (if (db/get-in project [:current-nrepl :session-id])
        (tasks/run-background-task!
         project
         loading-msg
         (fn [_indicator]
           (app-manager/invoke-later!
            {:invoke-fn (fn []
                          (let [{:keys [status err] :as response} (eval-fn editor)]
                            (if (and (contains? status "eval-error") err)
                              (ui.hint/show-repl-error :message err :editor editor)
                              (ui.hint/show-repl-info :message (success-msg-fn response) :editor editor))))})))
        (ui.hint/show-error :message "No REPL connected" :editor editor)))))

(defn load-file-action [^AnActionEvent event]
  (when-let [virtual-file ^VirtualFile (.getData event CommonDataKeys/VIRTUAL_FILE)]
    (eval-action
     event
     "REPL: Loading file"
     (fn [^Editor editor]
       (nrepl/load-file (.getProject editor) editor virtual-file))
     (fn [_response]
       (str "Loaded file " (.getPath ^VirtualFile virtual-file))))))

(defn eval-last-sexpr-action [^AnActionEvent event]
  (eval-action
   event
   "REPL: Evaluating"
   (fn [^Editor editor]
     (let [[row col] (util/editor->cursor-position editor)
           text (.getText (.getDocument editor))
           root-zloc (z/of-string text)
           zloc (parser/find-form-at-pos root-zloc (inc row) col)
           code (z/string zloc)
           ns (some-> (parser/find-namespace root-zloc) z/string)]
       (nrepl/eval {:project (.getProject editor) :code code :ns ns})))
   (fn [response]
     (string/join "\n" (:value response)))))

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
           ns (some-> (parser/find-namespace root-zloc) z/string)]
       (nrepl/eval {:project (.getProject editor) :code code :ns ns})))
   (fn [response]
     (string/join "\n" (:value response)))))

(defn switch-ns-action [^AnActionEvent event]
  (eval-action
   event
   "REPL: Switching ns"
   (fn [^Editor editor]
     (let [text (.getText (.getDocument editor))
           root-zloc (z/of-string text)
           zloc (parser/find-namespace root-zloc)
           namespace (z/string zloc)]
       (nrepl/eval {:project (.getProject editor) :code (format "(in-ns '%s)" namespace) :ns namespace})))
   (fn [response]
     (string/join "\n" (:value response)))))
