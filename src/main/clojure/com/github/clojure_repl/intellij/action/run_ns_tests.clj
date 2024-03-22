(ns com.github.clojure-repl.intellij.action.run-ns-tests
  (:gen-class
   :name com.github.clojure_repl.intellij.action.RunNsTests
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.clojure-repl.intellij.tests :as tests]
   [com.github.clojure-repl.intellij.ui.hint :as ui.hint]
   [rewrite-clj.zip :as z])
  (:import
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]))

(set! *warn-on-reflection* true)

(defn -actionPerformed [_ ^AnActionEvent event]
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (if (-> @db/db* :current-nrepl :session-id)
      (let [text (.getText (.getDocument editor))
            root-zloc (z/of-string text)
            zloc (parser/find-namespace root-zloc)
            ns (z/string zloc)]
        (tests/run
         :editor editor
         :ns ns))
      (ui.hint/show-error :message "No REPL connected" :editor editor))))
