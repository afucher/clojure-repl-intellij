(ns com.github.clojure-repl.intellij.action.eval-last-sexp
  (:gen-class
   :name com.github.clojure_repl.intellij.action.EvalLastSexp
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.clojure-repl.intellij.ui.hint :as ui.hint]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.tasks :as tasks]
   [com.github.ericdallo.clj4intellij.util :as util]
   [rewrite-clj.zip :as z]
   [clojure.string :as string])
  (:import
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]))

(set! *warn-on-reflection* true)

(defn -actionPerformed [_ ^AnActionEvent event]
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
