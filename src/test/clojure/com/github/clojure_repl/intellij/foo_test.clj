(ns com.github.clojure-repl.intellij.foo-test
  (:require
   [clojure.test :refer [deftest is]])
  (:import
   [com.intellij.openapi.command WriteCommandAction]
   [com.intellij.testFramework LightProjectDescriptor]
   [com.intellij.testFramework.fixtures IdeaTestFixtureFactory]
   [com.intellij.util ThrowableRunnable]))

(set! *warn-on-reflection* true)

;; TODO migrate these functions to clj4intellij app-manager
(defn write-command-action [project run-fn]
  (.run (WriteCommandAction/writeCommandAction project)
        (reify ThrowableRunnable
          (run [_]
            (run-fn)))))

(defn setup []
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
     (fn [] (.openFileInEditor fixture deps-file)))))
