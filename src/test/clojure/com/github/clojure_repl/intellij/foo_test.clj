(ns com.github.clojure-repl.intellij.foo-test
  (:require
   [clojure.test :refer [deftest is]])
  (:import
   [com.intellij.openapi.command WriteCommandAction]
   [com.intellij.testFramework LightProjectDescriptor]
   [com.intellij.testFramework.fixtures IdeaTestFixtureFactory]
   [com.intellij.util ThrowableRunnable]))

(set! *warn-on-reflection* true)

(deftest foo-test
  (let [factory (IdeaTestFixtureFactory/getFixtureFactory)
        fixture (-> factory
                    (.createLightFixtureBuilder LightProjectDescriptor/EMPTY_PROJECT_DESCRIPTOR "fooo")
                    (.getFixture))
        fixture-2 (.createCodeInsightFixture factory fixture)
        _ (.setUp fixture-2)
        virtual-file (.createFile fixture-2 "deps.edn" "{}")]
    (is (.getProject fixture-2))
    (is virtual-file)
    (.run (WriteCommandAction/writeCommandAction (.getProject fixture-2))
          (reify ThrowableRunnable
            (run [_]
              (.openFileInEditor fixture-2 virtual-file))))))
