(ns com.github.clojure-repl.intellij.foo-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager])
  (:import
   [com.intellij.testFramework LightProjectDescriptor]
   [com.intellij.testFramework.fixtures IdeaTestFixtureFactory]))

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
       (app-manager/write-action! {:run-fn (fn [] (.openFileInEditor fixture-2 virtual-file))}))
  (println "-------------------------------------------->")

  (is true))
