(ns com.github.clojure-repl.intellij.action.load-file
  (:gen-class
   :name com.github.clojure_repl.intellij.action.LoadFile
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [clojure.java.io :as io]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.hint :as ui.hint]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.tasks :as tasks])
  (:import
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.vfs VirtualFile]))

(set! *warn-on-reflection* true)

(defn -actionPerformed [_ ^AnActionEvent event]
  (when-let [vf ^VirtualFile (.getData event CommonDataKeys/VIRTUAL_FILE)]
    (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
      (if (-> @db/db* :current-nrepl :session-id)
        (tasks/run-background-task!
         (.getProject editor)
         "Clojure REPL load file"
         (fn [_indicator]
           (let [path (.getCanonicalPath vf)
                 file (io/file path)
                 {:keys [err]} (nrepl/load-file (-> event .getProject .getName) file)]
             (app-manager/invoke-later!
              {:invoke-fn (fn []
                            (if err
                              (ui.hint/show-repl-error :message err :editor editor)
                              (ui.hint/show-repl-info :message (str "Loaded file " path) :editor editor)))}))))
        (ui.hint/show-error :message "No REPL connected" :editor editor)))))
