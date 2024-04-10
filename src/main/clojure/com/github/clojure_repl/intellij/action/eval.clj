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

(defn load-file-action [^AnActionEvent event]
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (let [project (.getProject editor)]
      (if (db/get-in project [:current-nrepl :session-id])
        (tasks/run-background-task!
         (.getProject editor)
         "REPL: Loading file"
         (fn [_indicator]
           (let [virtual-file (.getData event CommonDataKeys/VIRTUAL_FILE)
                 {:keys [status err]} (nrepl/load-file project editor virtual-file)]
             (app-manager/invoke-later!
              {:invoke-fn (fn []
                            (if (and (contains? status "eval-error") err)
                              (ui.hint/show-repl-error :message err :editor editor)
                              (ui.hint/show-repl-info :message (str "Loaded file " (.getPath ^VirtualFile virtual-file)) :editor editor)))}))))
        (ui.hint/show-error :message "No REPL connected" :editor editor)))))

(defn eval-last-sexpr-action [^AnActionEvent event]
  ;; TODO change for listeners here or a better way to know which repl is related to current opened file
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (let [[row col] (util/editor->cursor-position editor)
          project (.getData event CommonDataKeys/PROJECT)
          text (.getText (.getDocument editor))]
      (if (db/get-in project [:current-nrepl :session-id])
        (tasks/run-background-task!
         (.getProject editor)
         "REPL: Evaluating"
         (fn [_indicator]
           (let [root-zloc (z/of-string text)
                 zloc (parser/find-form-at-pos root-zloc (inc row) col)
                 code (z/string zloc)
                 ns (some-> (parser/find-namespace root-zloc) z/string)
                 {:keys [value err]} (nrepl/eval {:project project :code code :ns ns})]
             (app-manager/invoke-later!
              {:invoke-fn (fn []
                            (if err
                              (ui.hint/show-repl-error :message err :editor editor)
                              (ui.hint/show-repl-info :message (string/join "\n" value) :editor editor)))}))))
        (ui.hint/show-error :message "No REPL connected" :editor editor)))))

(defn switch-ns-action [^AnActionEvent event]
  ;; TODO change for listeners here or a better way to know which repl is related to current opened file
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (let [project (.getData event CommonDataKeys/PROJECT)]
      (if (db/get-in project [:current-nrepl :session-id])
        (let [text (.getText (.getDocument editor))
              root-zloc (z/of-string text)
              zloc (parser/find-namespace root-zloc)
              namespace (z/string zloc)
              {:keys [value err]} (nrepl/eval {:project project :code (format "(in-ns '%s)" namespace) :ns namespace})]
          (if err
            (ui.hint/show-repl-error :message err :editor editor)
            (ui.hint/show-repl-info :message (string/join "\n" value) :editor editor)))
        (ui.hint/show-error :message "No REPL connected" :editor editor)))))
