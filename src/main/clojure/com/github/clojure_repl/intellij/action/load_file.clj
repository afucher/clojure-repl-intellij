(ns com.github.clojure-repl.intellij.action.load-file
  (:gen-class
   :name com.github.clojure_repl.intellij.action.LoadFile
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.repl-hint :as ui.repl-hint])
  (:import
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.vfs VirtualFile]))

(set! *warn-on-reflection* true)

(defn -actionPerformed [_ ^AnActionEvent event]
  (when-let [vf ^VirtualFile (.getData event CommonDataKeys/VIRTUAL_FILE)]
    (let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)
          path (.getCanonicalPath vf)]
      (nrepl/load-file (-> event .getProject .getName) path)
      (ui.repl-hint/show-info (str "Loaded file " path) editor))))
