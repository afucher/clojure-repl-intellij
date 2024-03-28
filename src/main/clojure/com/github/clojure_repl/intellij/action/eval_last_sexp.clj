(ns com.github.clojure-repl.intellij.action.eval-last-sexp
  (:gen-class
   :name com.github.clojure_repl.intellij.action.EvalLastSexp
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [com.github.clojure-repl.intellij.configuration.factory.base :as config.factory.base]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.clojure-repl.intellij.ui.hint :as ui.hint]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.tasks :as tasks]
   [com.github.ericdallo.clj4intellij.util :as util]
   [rewrite-clj.zip :as z])
  (:import
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]))

(set! *warn-on-reflection* true)

(defn -actionPerformed [_ ^AnActionEvent event]
  ;; TODO change for listeners here or a better way to know which repl is related to current opened file
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (if (-> @db/db* :current-nrepl :session-id)
      (let [[row col] (util/editor->cursor-position editor)
            project (.getData event CommonDataKeys/PROJECT)
            text (.getText (.getDocument editor))]
        (tasks/run-background-task!
         (.getProject editor)
         "REPL: Evaluating"
         (fn [_indicator]
           (let [root-zloc (z/of-string text)
                 zloc (parser/find-form-at-pos root-zloc (inc row) col)
                 code (z/string zloc)
                 {:keys [value out err]} (nrepl/eval {:project project :code code})]
              ;; TODO how we can avoid coupling config.repl ns with this?
              ;; maybe have a listener only for stdout?
             (when out
               (ui.repl/append-result-text (:console @config.factory.base/current-repl*) out))
             (app-manager/invoke-later!
              {:invoke-fn (fn []
                            (if err
                              (ui.hint/show-repl-error :message err :editor editor)
                              (ui.hint/show-repl-info :message value :editor editor)))})))))

      (ui.hint/show-error :message "No REPL connected" :editor editor))))
