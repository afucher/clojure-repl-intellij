(ns com.github.ericdallo.clj4intellij.test
  "Test utilities for clj4intellij"
  (:import
   [com.intellij.testFramework EdtTestUtil LightProjectDescriptor]
   [com.intellij.testFramework.fixtures CodeInsightTestFixture IdeaTestFixtureFactory TestFixtureBuilder]
   [com.intellij.testFramework.fixtures.impl IdeaTestFixtureFactoryImpl]
   [com.intellij.util ThrowableRunnable]
   [com.intellij.util.ui UIUtil]))


(set! *warn-on-reflection* true)

(defn setup
  "Setup fixture factory, with an empty project and return an instance of CodeInsightTestFixture
   
   ref: https://github.com/JetBrains/intellij-community/blob/2766d0bf1cec76c0478244f6ad5309af527c245e/platform/testFramework/src/com/intellij/testFramework/fixtures/CodeInsightTestFixture.java"
  ^CodeInsightTestFixture
  [project-name]
  (let [factory ^IdeaTestFixtureFactoryImpl (IdeaTestFixtureFactory/getFixtureFactory)
        raw-fixture (-> factory
                        ^TestFixtureBuilder (.createLightFixtureBuilder LightProjectDescriptor/EMPTY_PROJECT_DESCRIPTOR project-name)
                        (.getFixture))
        fixture (.createCodeInsightFixture factory raw-fixture)]
    (.setUp fixture)
    fixture))

(defn dispatch-all
  "API for `UIUtil/dispatchAllInvocationEvents`.
  
  ref:https://github.com/JetBrains/intellij-community/blob/2766d0bf1cec76c0478244f6ad5309af527c245e/platform/util/ui/src/com/intellij/util/ui/UIUtil.java#L1450"
  []
  (EdtTestUtil/runInEdtAndWait
   (reify ThrowableRunnable
     (run [_]
       (UIUtil/dispatchAllInvocationEvents)))))

(defn dispatch-all-until
  "Dispatch all events in the EDT until condition is met.
   Returns a promise which can be `deref` to await the the condition to be met.

   Receives a map with the following keys:
   - `:cond-fn` - a function that returns true when the condition is met.
   - `:millis` - the time to wait between dispatches (default: 100)

   See `dispatch-all` for more information."
  [{:keys [cond-fn millis]
    :or {millis 100}}]
  (let [p (promise)]
    (future
      (loop []
        (if (cond-fn)
          (deliver p true)
          (do
            (dispatch-all)
            (Thread/sleep millis)
            (recur)))))
    p))
