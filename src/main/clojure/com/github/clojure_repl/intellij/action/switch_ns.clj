(ns com.github.clojure-repl.intellij.action.switch-ns
  (:gen-class
   :name com.github.clojure_repl.intellij.action.SwitchNs
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.parser :as parser]
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
          text (.getText (.getDocument editor))
          root-zloc (z/of-string text)
          zloc (parser/find-namespace root-zloc)
          namespace (z/string zloc)
          {:keys [value err]} (nrepl/eval {:project project :code (format "(in-ns '%s)" namespace)})]
      (if err
        (ui.repl-hint/show-error err editor)
        (ui.repl-hint/show-info value editor)))
    (.showErrorHint (HintManager/getInstance) (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE) "No REPL connected" (HintManager/RIGHT))))
