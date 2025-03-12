(ns com.github.clojure-repl.intellij.foo-test
  (:require
   [clojure.test :refer [deftest is]])
  (:import
   [com.intellij.testFramework LightProjectDescriptor]
   [com.intellij.testFramework.fixtures IdeaTestFixtureFactory]))

(set! *warn-on-reflection* true)

(deftest foo-test
  (let [factory (IdeaTestFixtureFactory/getFixtureFactory)
        fixture (-> factory
                    (.createLightFixtureBuilder LightProjectDescriptor/EMPTY_PROJECT_DESCRIPTOR "fooo")
                    (.getFixture))
        fixture-2 (.createCodeInsightFixture factory fixture)]
    (.setUp fixture-2))
  (println "-------------------------------------------->")

  (is true))
