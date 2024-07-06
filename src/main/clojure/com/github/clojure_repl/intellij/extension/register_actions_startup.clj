(ns com.github.clojure-repl.intellij.extension.register-actions-startup
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.RegisterActionsStartup
   :implements [com.intellij.openapi.startup.StartupActivity
                com.intellij.openapi.project.DumbAware])
  (:require
   [com.github.clojure-repl.intellij.action.eval :as a.eval]
   [com.github.clojure-repl.intellij.action.test :as a.test]
   [com.github.ericdallo.clj4intellij.action :as action])
  (:import
   [com.github.clojure_repl.intellij Icons]
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* true)

(defn -runActivity [_this ^Project _project]
  (action/register-action! :id "ClojureREPL.RunCursorTest"
                           :title "Run test at cursor"
                           :description "Run test at cursor"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt T" :second "shift alt T" :replace-all true}
                           :on-performed #'a.test/run-cursor-test-action)
  (action/register-action! :id "ClojureREPL.RunNsTests"
                           :title "Run namespace tests"
                           :description "Run all namespaces tests"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt T" :second "shift alt N" :replace-all true}
                           :on-performed #'a.test/run-ns-tests-action)
  (action/register-action! :id "ClojureREPL.LoadFile"
                           :title "Load file to REPL"
                           :description "Load file to REPL"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt L" :replace-all true}
                           :on-performed #'a.eval/load-file-action)
  (action/register-action! :id "ClojureREPL.EvalLastSexp"
                           :title "Eval last sexp"
                           :description "Eval the expression preceding cursor"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt E" :replace-all true}
                           :on-performed #'a.eval/eval-last-sexpr-action)
  (action/register-action! :id "ClojureREPL.EvalDefun"
                           :title "Eval defun at cursor"
                           :description "Evaluate the current toplevel form"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt D" :replace-all true}
                           :on-performed #'a.eval/eval-defun-action)
  (action/register-action! :id "ClojureREPL.ClearReplOutput"
                           :title "Clear REPL output"
                           :description "Clear REPL output"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt R" :second "shift alt C"  :replace-all true}
                           :on-performed #'a.eval/clear-repl-output-action)
  (action/register-action! :id "ClojureREPL.SwitchNs"
                           :title "Switch REPL namespace"
                           :description "Switch REPL namespace to current opened file namespace"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt N" :replace-all true}
                           :on-performed #'a.eval/switch-ns-action)

  (action/register-group! :id "ClojureREPL.ReplActions"
                          :popup true
                          :text "Clojure REPL"
                          :icon Icons/CLOJURE_REPL
                          :children [{:type :add-to-group :group-id "ToolsMenu" :anchor :first}
                                     {:type :add-to-group :group-id "EditorPopupMenu" :anchor :before :relative-to "RefactoringMenu"}
                                     {:type :separator}
                                     {:type :reference :ref "ClojureREPL.RunCursorTest"}
                                     {:type :reference :ref "ClojureREPL.RunNsTests"}
                                     {:type :separator}
                                     {:type :reference :ref "ClojureREPL.LoadFile"}
                                     {:type :reference :ref "ClojureREPL.EvalLastSexp"}
                                     {:type :reference :ref "ClojureREPL.EvalDefun"}
                                     {:type :separator}
                                     {:type :reference :ref "ClojureREPL.ClearReplOutput"}
                                     {:type :reference :ref "ClojureREPL.SwitchNs"}
                                     {:type :separator}]))
