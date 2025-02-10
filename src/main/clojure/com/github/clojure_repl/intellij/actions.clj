(ns com.github.clojure-repl.intellij.actions 
  (:import
   [com.intellij.openapi.actionSystem AnActionEvent CommonDataKeys]
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* true)

(defn action-event->project ^Project [^AnActionEvent event]
  (let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)
        project ^Project (or (.getData event CommonDataKeys/PROJECT)
                             (.getProject editor))]
    project))
