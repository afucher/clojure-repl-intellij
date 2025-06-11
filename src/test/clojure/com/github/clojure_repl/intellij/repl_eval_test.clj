(ns com.github.clojure-repl.intellij.repl-eval-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.github.clojure-repl.intellij.configuration.factory.local :as config.factory.local]
   [com.github.clojure-repl.intellij.utils :refer [repl-content
                                                   stop-all-configurations
                                                   wait-console-ui-creation]]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.test :as clj4intellij.test])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplRunConfigurationType]
   [com.intellij.execution ProgramRunnerUtil RunManager]
   [com.intellij.execution.executors DefaultRunExecutor]
   [com.intellij.openapi.util ThrowableComputable]
   [com.intellij.testFramework EditorTestUtil EdtTestUtil]
   [java.awt.event KeyEvent]))

(set! *warn-on-reflection* true)

(def fixtures* (atom nil))

(use-fixtures :once (fn [f]
                      (f)
                      (stop-all-configurations (.getProject @fixtures*))))

(defn execute-configuration
  "API for ProgramRunnerUtil/executeConfiguration
   
   ref: https://github.com/JetBrains/intellij-community/blob/2766d0bf1cec76c0478244f6ad5309af527c245e/platform/execution-impl/src/com/intellij/execution/ProgramRunnerUtil.java#L46"
  [configuration-instance]
  (ProgramRunnerUtil/executeConfiguration
   configuration-instance
   (DefaultRunExecutor/getRunExecutorInstance)))

(defn eval-code-on-repl [repl-content text]
  (let [editor (.getEditor repl-content)
        component (.getContentComponent editor)
        key-enter-event (KeyEvent. component KeyEvent/KEY_PRESSED (System/currentTimeMillis) 0 KeyEvent/VK_ENTER Character/MIN_VALUE)]
    (EdtTestUtil/runInEdtAndGet
     (reify ThrowableComputable
       (compute [_]
         (doseq [char text]
           (EditorTestUtil/performTypingAction editor char))
         (.dispatchEvent component key-enter-event)
         nil)))))

(deftest repl-eval-test
  (let [project-name "clojure.core"
        fixture (clj4intellij.test/setup project-name)
        _ (reset! fixtures* fixture)
        deps-file (.createFile fixture "deps.edn" "{}")
        project (.getProject fixture)]
    (is (= project-name (.getName project)))
    (is deps-file)

    (app-manager/write-command-action
     project
     (fn [] (.openFileInEditor fixture deps-file)))

    (let [run-manager (RunManager/getInstance project)
          configuration (config.factory.local/configuration-factory (ReplRunConfigurationType.))
          configuration-instance (.createConfiguration run-manager "Local REPL" configuration)]
      (doto (-> configuration-instance .getConfiguration .getOptions)
        (.setProject project-name)
        (.setProjectType "clojure"))
      (execute-configuration configuration-instance))

    (wait-console-ui-creation project)

    (testing "user input evaluation"
      (let [repl-content (repl-content project)]

        @(clj4intellij.test/dispatch-all-until
          {:cond-fn (fn [] (str/ends-with? (.getText repl-content) "user> "))})

        (eval-code-on-repl repl-content "(+ 1 1)\n")
        (clj4intellij.test/dispatch-all)

        (let [content (.getText repl-content)]
          (is (str/ends-with? content "user> (+ 1 1)\n=> 2\nuser> ")))))))

