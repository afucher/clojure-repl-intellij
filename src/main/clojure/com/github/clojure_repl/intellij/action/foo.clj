(ns com.github.clojure-repl.intellij.action.foo
  (:gen-class
   :name com.github.clojure_repl.intellij.action.FooAction
   :extends com.intellij.openapi.actionSystem.AnAction)
  (:require
   [nrepl.core :as nrepl])
  (:import
    [com.intellij.codeInsight.hint HintManager]
    [com.intellij.find.findUsages FindUsagesOptions]
    [com.intellij.openapi.actionSystem CommonDataKeys]
    [com.intellij.openapi.actionSystem AnActionEvent]
    [com.intellij.openapi.editor Document Editor]
    [com.intellij.openapi.project Project]
    [com.intellij.openapi.util TextRange]
    [com.intellij.openapi.vfs VirtualFile]
    [com.intellij.openapi.ui Messages]
    [com.intellij.psi PsiElement]
    [com.intellij.usages
     Usage
     UsageInfo2UsageAdapter
     UsageTarget
     UsageViewManager
     UsageViewPresentation]
    [com.intellij.usageView UsageInfo]))


(set! *warn-on-reflection* true)

(defn connect-load-file
  [file]
  (with-open [conn ^nrepl.transport.FnTransport (nrepl/connect :port 63127)]
    (-> (nrepl/client conn 1000)    ; message receive timeout required
        (nrepl/message {:op "load-file" :file (slurp file)})
        doall
        println)))

(defn -actionPerformed [_ ^AnActionEvent event]
  (when-let [vf ^VirtualFile (.getData event CommonDataKeys/VIRTUAL_FILE)]
    (let [path (.getCanonicalPath vf)]
      (connect-load-file path)
      (Messages/showMessageDialog (.toString ^String path) "Title" (Messages/getInformationIcon)))))
