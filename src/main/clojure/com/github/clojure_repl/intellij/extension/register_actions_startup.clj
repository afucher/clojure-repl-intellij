(ns com.github.clojure-repl.intellij.extension.register-actions-startup
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.RegisterActionsStartup
   :implements [com.intellij.openapi.startup.ProjectActivity
                com.intellij.openapi.project.DumbAware])
  (:require
   [com.github.clojure-repl.intellij.action.custom-code-actions :as custom-code-actions]
   [com.github.clojure-repl.intellij.action.eval :as a.eval]
   [com.github.clojure-repl.intellij.action.test :as a.test]
   [com.github.clojure-repl.intellij.actions :as actions]
   [com.github.clojure-repl.intellij.config :as config]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.ericdallo.clj4intellij.action :as action]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.github.clojure_repl.intellij Icons]
   [com.intellij.icons AllIcons$Actions]
   [com.intellij.openapi.actionSystem ActionManager AnActionEvent DefaultActionGroup]
   [com.intellij.openapi.project DumbAwareAction Project]
   [kotlinx.coroutines CoroutineScope]))

(set! *warn-on-reflection* true)

(defn -execute
  "Shortcuts: https://github.com/JetBrains/intellij-community/blob/master/platform/platform-resources/src/keymaps/%24default.xml"
  [_this ^Project _project ^CoroutineScope _]
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
  (action/register-action! :id "ClojureREPL.ReRunTest"
                           :title "Re-run last test"
                           :description "Re-run last executed test"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt T" :second "shift alt A" :replace-all true}
                           :on-performed #'a.test/re-run-test-action)
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
                           :keyboard-shortcut {:first "shift alt R" :second "shift alt C"  :replace-all true}
                           :action (proxy+
                                    ["Clear REPL output" "Clear REPL output" AllIcons$Actions/GC]
                                    DumbAwareAction
                                    (update
                                     [_ ^AnActionEvent event]
                                     (let [project (actions/action-event->project event)]
                                       (.setEnabled (.getPresentation event) (boolean (nrepl/active-client? project)))))
                                    (actionPerformed
                                     [_ event]
                                     (a.eval/clear-repl-output-action event))))

  (action/register-action! :id "ClojureREPL.SwitchNs"
                           :title "Switch REPL namespace"
                           :description "Switch REPL namespace to current opened file namespace"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt N" :replace-all true}
                           :on-performed #'a.eval/switch-ns-action)
  (action/register-action! :id "ClojureREPL.RefreshAll"
                           :title "Refresh all namespaces"
                           :description "Refresh all namespaces"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt R" :second "shift alt A" :replace-all true}
                           :on-performed #'a.eval/refresh-all-action)
  (action/register-action! :id "ClojureREPL.RefreshChanged"
                           :title "Refresh changed namespaces"
                           :description "Refresh changed namespaces"
                           :icon Icons/CLOJURE_REPL
                           :keyboard-shortcut {:first "shift alt R" :second "shift alt R" :replace-all true}
                           :on-performed #'a.eval/refresh-changed-action)
  (action/register-action! :id "ClojureREPL.HistoryUp"
                           :title "Moves up in history"
                           :description "Moves up in history"
                           :icon AllIcons$Actions/PreviousOccurence
                           :keyboard-shortcut {:first "control PAGE_UP" :replace-all true}
                           :on-performed #'a.eval/history-up-action)
  (action/register-action! :id "ClojureREPL.HistoryDown"
                           :title "Moves down in history"
                           :description "Moves down in history"
                           :icon AllIcons$Actions/NextOccurence
                           :keyboard-shortcut {:first "control PAGE_DOWN" :replace-all true}
                           :on-performed #'a.eval/history-down-action)
  (action/register-action! :id "ClojureREPL.Interrupt"
                           :keyboard-shortcut {:first "shift alt R" :second "shift alt S" :replace-all true}
                           :action (proxy+
                                    ["Interrupts session evaluation" "Interrupts session evaluation" AllIcons$Actions/Cancel]
                                    DumbAwareAction
                                    (update
                                     [_ ^AnActionEvent event]
                                     (let [project (actions/action-event->project event)]
                                       (.setEnabled (.getPresentation event) (boolean (nrepl/evaluating? project)))))
                                    (actionPerformed
                                     [_ event]
                                     (a.eval/interrupt event))))
  (action/register-action! :id "ClojureREPL.ReloadCustomActions"
                           :action (proxy+
                                    ["Reload custom actions" "Reload custom actions" AllIcons$Actions/Refresh]
                                    DumbAwareAction
                                    (actionPerformed
                                     [_ event]
                                     (let [action-manager (ActionManager/getInstance)
                                           custom-actions (.getActionIdList action-manager "ClojureREPL.Custom")
                                           project (actions/action-event->project event)]
                                       (doseq [id custom-actions]
                                         (when (not= id custom-code-actions/group-id)
                                           (.unregisterAction action-manager id)))
                                       (when-let [group (.getAction action-manager custom-code-actions/group-id)]
                                         (.removeAll ^DefaultActionGroup group))
                                       (custom-code-actions/register-custom-code-actions (config/eval-code-actions-from-user))
                                       (custom-code-actions/register-custom-code-actions (config/from-project project) project)))))

  (action/register-group! :id "ClojureREPL.ReplActions"
                          :popup true
                          :text "Clojure REPL"
                          :icon Icons/CLOJURE_REPL
                          :children [{:type :add-to-group :group-id "ToolsMenu" :anchor :first}
                                     {:type :add-to-group :group-id "EditorPopupMenu" :anchor :before :relative-to "RefactoringMenu"}
                                     {:type :separator}
                                     {:type :reference :ref "ClojureREPL.RunCursorTest"}
                                     {:type :reference :ref "ClojureREPL.RunNsTests"}
                                     {:type :reference :ref "ClojureREPL.ReRunTest"}
                                     {:type :separator}
                                     {:type :reference :ref "ClojureREPL.LoadFile"}
                                     {:type :reference :ref "ClojureREPL.EvalLastSexp"}
                                     {:type :reference :ref "ClojureREPL.EvalDefun"}
                                     {:type :separator}
                                     {:type :reference :ref "ClojureREPL.ClearReplOutput"}
                                     {:type :reference :ref "ClojureREPL.RefreshAll"}
                                     {:type :reference :ref "ClojureREPL.RefreshChanged"}
                                     {:type :reference :ref "ClojureREPL.SwitchNs"}
                                     {:type :separator}])
  (action/register-group! :id custom-code-actions/group-id
                          :popup true
                          :text "Custom code actions"
                          :icon Icons/CLOJURE_REPL
                          :children [{:type :add-to-group :group-id "ClojureREPL.ReplActions" :anchor :last}])
  (custom-code-actions/register-custom-code-actions (config/eval-code-actions-from-user)))

