(ns com.github.clojure-repl.intellij.action.test
  (:require
   [com.github.clojure-repl.intellij.tests :as tests])
  (:import
   [com.intellij.openapi.actionSystem CommonDataKeys]
   [com.intellij.openapi.actionSystem AnActionEvent]
   [com.intellij.openapi.editor Editor]))

(set! *warn-on-reflection* true)

(defn run-ns-tests-action [^AnActionEvent event]
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (tests/run-ns-tests editor)))

(defn run-cursor-test-action [^AnActionEvent event]
  (when-let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)]
    (tests/run-at-cursor editor)))
