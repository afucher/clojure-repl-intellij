(ns com.github.clojure-repl.intellij.extension.register-actions-startup
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.RegisterActionsStartup
   :implements [com.intellij.openapi.startup.StartupActivity
                com.intellij.openapi.project.DumbAware])
  (:require
   [com.github.clojure-repl.intellij.action.eval :as a.eval]
   [com.github.clojure-repl.intellij.action.test :as a.test]
   [com.github.ericdallo.clj4intellij.action :as action]
   [com.github.clojure-repl.intellij.nrepl :as nrepl])
  (:import
   [com.github.clojure_repl.intellij Icons]
   [com.intellij.icons AllIcons$Actions]
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* true)

(do (in-ns 'com.github.ericdallo.clj4intellij.action)

    (require '[com.github.ericdallo.clj4intellij.logger :as logger])
    (import [com.intellij.openapi.actionSystem AnActionEvent])
    (defn register-action!
          "Dynamically register an action if not already registered."
          [& {:keys [id title description icon use-shortcut-of keyboard-shortcut on-performed enabled-fn]
              :or {enabled-fn (constantly true)}}]
          (let [manager (ActionManager/getInstance)
                keymap-manager (KeymapManager/getInstance)
                keymap (.getActiveKeymap keymap-manager)
                action (proxy+
                        [^String title ^String description ^Icon icon]
                        DumbAwareAction
                         (update [_ ^AnActionEvent event]
                                 (.setEnabled (.getPresentation event) (enabled-fn event)))
                         (actionPerformed [_ event] (on-performed event)))]
               (when-not (.getAction manager id)
                         (.registerAction manager id action)
                         (when use-shortcut-of
                               (.addShortcut keymap
                                             id
                                             (first (.getShortcuts (.getShortcutSet (.getAction manager use-shortcut-of))))))
                         (when keyboard-shortcut
                               (let [k-shortcut (KeyboardShortcut. (KeyStroke/getKeyStroke ^String (:first keyboard-shortcut))
                                                                   (some-> ^String (:second keyboard-shortcut) KeyStroke/getKeyStroke))]
                                    (when (empty? (.getShortcuts keymap id))
                                          (.addShortcut keymap id k-shortcut))
                                    (when (:replace-all keyboard-shortcut)
                                          (doseq [[conflict-action-id shortcuts] (.getConflicts keymap id k-shortcut)]
                                                 (doseq [shortcut shortcuts]
                                                        (.removeShortcut keymap conflict-action-id shortcut))))))
                         action)))
    (in-ns 'com.github.clojure-repl.intellij.extension.register-actions-startup))

(comment
 (import [com.intellij.openapi.actionSystem ActionManager])
 (let [manager (ActionManager/getInstance)]
      (.unregisterAction manager "ClojureREPL.Interrupt"))

 )

(defn -runActivity
  "Shortcuts: https://github.com/JetBrains/intellij-community/blob/master/platform/platform-resources/src/keymaps/%24default.xml"
  [_this ^Project project]
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
                           :title "Clear REPL output"
                           :description "Clear REPL output"
                           :icon AllIcons$Actions/GC
                           :keyboard-shortcut {:first "shift alt R" :second "shift alt C"  :replace-all true}
                           :on-performed #'a.eval/clear-repl-output-action)
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
                           :title "Interrupts session evaluation"
                           :description "Interrupts session evaluation"
                           :icon AllIcons$Actions/Cancel
                           :keyboard-shortcut {:first "shift alt R" :second "shift alt S" :replace-all true}
                           :on-performed #'a.eval/interrupt
                           :enabled-fn (fn [_event]
                                           (nrepl/evaluating? project)))


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
                                     {:type :separator}]))
