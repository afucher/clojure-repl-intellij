(ns com.github.clojure-repl.intellij.extension.run-test-line-marker-provider
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.RunTestLineMarkerProvider
   :extends com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor)
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.tests :as tests])
  (:import
   [com.github.clojure_repl.intellij Icons]
   [com.intellij.codeInsight.daemon GutterIconNavigationHandler LineMarkerInfo]
   [com.intellij.openapi.editor.markup GutterIconRenderer$Alignment]
   [com.intellij.openapi.fileEditor FileEditorManager]
   [com.intellij.psi PsiElement]
   [com.intellij.psi.impl.source.tree LeafPsiElement]))

(set! *warn-on-reflection* true)

(defn -getName [_]
  "Run test")

(defn -getIcon [_]
  Icons/CLOJURE_REPL)

(defn -getLineMarkerInfo [_ ^PsiElement element]
  (when (and (instance? LeafPsiElement element)
             (or (string/ends-with? (.getText element) "-test")
                 (contains? #{"deftest" "ns"} (some-> element .getPrevSibling .getPrevSibling .getText))))
    (LineMarkerInfo.
     element
     (.getTextRange element)
     Icons/CLOJURE_REPL
     nil
     (reify GutterIconNavigationHandler
       (navigate [_ _event element]
         (let [element ^PsiElement element
               project (.getProject element)
               editor (.getSelectedTextEditor (FileEditorManager/getInstance project))]
           (.moveToOffset (.getCaretModel editor) (.getTextOffset element))
           (tests/run-at-cursor editor))))
     GutterIconRenderer$Alignment/CENTER)))
