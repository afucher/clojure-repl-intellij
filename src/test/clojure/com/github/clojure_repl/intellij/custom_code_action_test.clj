(ns com.github.clojure-repl.intellij.custom-code-action-test
  (:require
   [clojure.java.io :as io]
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
   [com.intellij.openapi.actionSystem ActionManager]
   [com.intellij.openapi.util ThrowableComputable]
   [com.intellij.testFramework EditorTestUtil EdtTestUtil]))

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

(deftest custom-code-action-from-config-test
  (let [project-name "test-custom-action"
        fixtures (clj4intellij.test/setup project-name)
        _ (reset! fixtures* fixtures)
        _ (.setTestDataPath fixtures "testdata")
        deps-file (.createFile fixtures "deps.edn" "{}")
        project (.getProject fixtures)]
    (is (= project-name (.getName project)))
    (is deps-file)

    (let [base-path (.getBasePath project)
          config-dir (io/file base-path ".clj-repl-intellij")
          config-file (io/file config-dir "config.edn")]
      (.mkdirs config-dir)
      (spit config-file (slurp "testdata/config.edn")))


    (app-manager/write-command-action
     project
     (fn [] (.openFileInEditor fixtures deps-file)))

    (let [run-manager (RunManager/getInstance project)
          configuration (config.factory.local/configuration-factory (ReplRunConfigurationType.))
          configuration-instance (.createConfiguration run-manager "Local REPL" configuration)]
      (doto (-> configuration-instance .getConfiguration .getOptions)
        (.setProject project-name)
        (.setProjectType "clojure"))
      (execute-configuration configuration-instance))

    (wait-console-ui-creation project)

    (testing "custom code action from project config"
      (let [repl-content (repl-content project)]

        @(clj4intellij.test/dispatch-all-until
          {:cond-fn (fn [] (str/ends-with? (.getText repl-content) "user> "))})

        (let [action-manager (ActionManager/getInstance)
              action-id "ClojureREPL.Custom.TestCustomAction.HelloWorldAction"
              custom-action (.getAction action-manager action-id)]

          (is (not (nil? custom-action)) "Custom action should be registered")

          (EdtTestUtil/runInEdtAndGet
           (reify ThrowableComputable
             (compute [_]
               (let [editor (.getEditor fixtures)]
                 (EditorTestUtil/executeAction editor action-id false))
               nil)))

          (clj4intellij.test/dispatch-all)

          (let [content (.getText repl-content)]
            (is (str/includes? content "Hello world\n") "Custom action should print Hello world to REPL")))))))

