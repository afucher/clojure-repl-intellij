(ns com.github.clojure-repl.intellij.foo-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.github.clojure-repl.intellij.configuration.factory.local :as config.factory.local]
   [com.github.clojure-repl.intellij.db :as db])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplRunConfigurationType]
   [com.intellij.execution ProgramRunnerUtil RunManager]
   [com.intellij.execution.executors DefaultRunExecutor]
   [com.intellij.execution.ui RunContentManager]
   [com.intellij.openapi.command WriteCommandAction]
   [com.intellij.testFramework LightProjectDescriptor]
   [com.intellij.testFramework.fixtures CodeInsightTestFixture IdeaTestFixtureFactory]
   [com.intellij.util ThrowableRunnable]))

(set! *warn-on-reflection* true)

;; TODO migrate these functions to clj4intellij app-manager
(defn write-command-action [project run-fn]
  (.run (WriteCommandAction/writeCommandAction project)
        (reify ThrowableRunnable
          (run [_]
            (run-fn)))))

(defn setup ^CodeInsightTestFixture []
  (let [factory (IdeaTestFixtureFactory/getFixtureFactory)
        raw-fixture (-> factory
                        (.createLightFixtureBuilder LightProjectDescriptor/EMPTY_PROJECT_DESCRIPTOR (str *ns*))
                        (.getFixture))
        fixture (.createCodeInsightFixture factory raw-fixture)]
    (.setUp fixture)
    (is (.getProject fixture))
    fixture))

(deftest foo-test
  (let [fixture (setup)
        deps-file (.createFile fixture "deps.edn" "{}")]
    (is deps-file)
    (write-command-action
     (.getProject fixture)
     (fn [] (.openFileInEditor fixture deps-file)))
    (is (.getEditor fixture))
    (let [project (.getProject fixture)
          run-manager (RunManager/getInstance project)
          configuration (config.factory.local/configuration-factory (ReplRunConfigurationType.))
          configuration-instance (.createConfiguration run-manager "Local REPL" configuration)]
      (ProgramRunnerUtil/executeConfiguration configuration-instance
                                              (DefaultRunExecutor/getRunExecutorInstance))
      ;; (Thread/sleep 2000)
      ;; (println '_______________ (.getExitCode (.getProcessHandler (.getSelectedContent (RunContentManager/getInstance project)))))
      (clojure.pprint/pprint @db/db*)

      #_())))
