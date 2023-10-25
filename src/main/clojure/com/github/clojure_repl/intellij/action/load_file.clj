(ns com.github.clojure-repl.intellij.action.load-file
  (:gen-class
   :name com.github.clojure_repl.intellij.action.LoadFile
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [com.github.clojure-repl.intellij.nrepl :as nrepl])
  (:import
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.vfs VirtualFile]))

(set! *warn-on-reflection* true)

(defn -actionPerformed [_ ^AnActionEvent event]
  (when-let [vf ^VirtualFile (.getData event CommonDataKeys/VIRTUAL_FILE)]
    (let [path (.getCanonicalPath vf)]
      (nrepl/load-file (-> event .getProject .getName) path))))
