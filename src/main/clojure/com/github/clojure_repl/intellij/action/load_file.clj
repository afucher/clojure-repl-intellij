(ns com.github.clojure-repl.intellij.action.load-file
  (:gen-class
   :name com.github.clojure_repl.intellij.action.LoadFile
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [clojure.java.io :as io]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.repl-hint :as ui.repl-hint])
  (:import
   [com.intellij.codeInsight.hint HintManager]
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.vfs VirtualFile]))

(set! *warn-on-reflection* true)

(defn -actionPerformed [_ ^AnActionEvent event]
  (when-let [vf ^VirtualFile (.getData event CommonDataKeys/VIRTUAL_FILE)]
    (if (-> @db/db* :current-nrepl :session-id)
      (let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)
            path (.getCanonicalPath vf)
            file (io/file path)
            {:keys [err]} (nrepl/load-file (-> event .getProject .getName) file)]
        (if err
          (ui.repl-hint/show-error err editor)
          (ui.repl-hint/show-info (str "Loaded file " path) editor)))
      (.showErrorHint (HintManager/getInstance) (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE) "No REPL connected" (HintManager/RIGHT)))))
