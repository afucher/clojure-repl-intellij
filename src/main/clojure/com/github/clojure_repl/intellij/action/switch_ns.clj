(ns com.github.clojure-repl.intellij.action.switch-ns
  (:gen-class
   :name com.github.clojure_repl.intellij.action.SwitchNs
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.clojure-repl.intellij.ui.hint :as ui.hint]
   [rewrite-clj.zip :as z])
  (:import
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]))

(set! *warn-on-reflection* true)

(defn -actionPerformed [_ ^AnActionEvent event]
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
