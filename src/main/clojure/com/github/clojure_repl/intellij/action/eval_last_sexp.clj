(ns com.github.clojure-repl.intellij.action.eval-last-sexp
  (:gen-class
   :name com.github.clojure_repl.intellij.action.EvalLastSexp
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [com.github.clojure-repl.intellij.configuration.repl :as config.repl]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.editor :as editor]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.clojure-repl.intellij.ui.repl-hint :as ui.repl-hint]
   [rewrite-clj.zip :as z])
  (:import
   [com.intellij.codeInsight.hint HintManager]
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]))

(set! *warn-on-reflection* true)

(defn -actionPerformed [_ ^AnActionEvent event]
  ;; TODO change for listeners here or a better way to know which repl is related to current opened file
  (if (-> @db/db* :current-nrepl :session-id)
    (let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)
          project (.getData event CommonDataKeys/PROJECT)
          [row col] (editor/editor->cursor-position editor)
          text (.getText (.getDocument editor))
          root-zloc (z/of-string text)
          zloc (parser/find-at-pos root-zloc (inc row) col)
          code (z/string zloc)
          {:keys [value out err]} (nrepl/eval {:project project :code code})]
      ;; TODO how we can avoid coupling config.repl ns with this?
      ;; maybe have a listener only for stdout?
      (when out
        (ui.repl/append-text (:console @config.repl/current-repl*) out))
      (if err
        (ui.repl-hint/show-error err editor)
        (ui.repl-hint/show-info value editor)))
    (.showErrorHint (HintManager/getInstance) (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE) "No REPL connected" (HintManager/RIGHT))))
