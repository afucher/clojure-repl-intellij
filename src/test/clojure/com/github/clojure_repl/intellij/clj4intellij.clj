(ns com.github.clojure-repl.intellij.clj4intellij
  (:require
   [clojure.test :refer [is]])
  (:import
   [com.intellij.execution ProgramRunnerUtil]
   [com.intellij.execution.executors DefaultRunExecutor]
   [com.intellij.openapi.command WriteCommandAction]
   [com.intellij.testFramework EdtTestUtil LightProjectDescriptor]
   [com.intellij.testFramework.fixtures CodeInsightTestFixture IdeaTestFixtureFactory]
   [com.intellij.util ThrowableRunnable]
   [com.intellij.util.ui UIUtil]))

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

(defn dispatch-all []
  (EdtTestUtil/runInEdtAndWait
   (reify ThrowableRunnable
     (run [_]
       (UIUtil/dispatchAllInvocationEvents)))))

(defn dispatch-all-until [condition]
  (let [p (promise)]
    (future
      (loop []
        (if (condition)
          (deliver p true)
          (do
            (dispatch-all)
            (Thread/sleep 100)
            (recur)))))
    p))

(defn execute-configuration [configuration-instance]
  (ProgramRunnerUtil/executeConfiguration
   configuration-instance
   (DefaultRunExecutor/getRunExecutorInstance)))
