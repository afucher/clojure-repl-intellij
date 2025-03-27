(ns com.github.clojure-repl.intellij.repl-eval-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [com.github.clojure-repl.intellij.clj4intellij :as clj4intellij]
   [com.github.clojure-repl.intellij.configuration.factory.local :as config.factory.local]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [seesaw.core :as seesaw])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplRunConfigurationType]
   [com.intellij.execution RunManager]
   [com.intellij.openapi.util ThrowableComputable]
   [com.intellij.testFramework EditorTestUtil EdtTestUtil]
   [java.awt.event KeyEvent]))

(set! *warn-on-reflection* true)

;;Move to a helper inside tests folder
(defn ^:private repl-content [project]
  (-> project
      (db/get-in [:console :ui])
      (seesaw/select [:#repl-content])))

(defn ensure-editor [project]
  (let [repl-content (repl-content project)]
    @(app-manager/invoke-later!
      {:invoke-fn (fn []
                    (.addNotify repl-content)
                    (.getEditor repl-content true))})))

(defn wait-console-ui-creation [project]
  @(clj4intellij/dispatch-all-until
    (fn [] (-> project
               (db/get-in [:console :ui]))))
  (ensure-editor project))



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
  (let [fixture (clj4intellij/setup)
        deps-file (.createFile fixture "deps.edn" "{}")
        project (.getProject fixture)
        project-name (.getName project)]
    (is deps-file)

    (clj4intellij/write-command-action
     project
     (fn [] (.openFileInEditor fixture deps-file)))

    (let [run-manager (RunManager/getInstance project)
          configuration (config.factory.local/configuration-factory (ReplRunConfigurationType.))
          configuration-instance (.createConfiguration run-manager "Local REPL" configuration)]
      (doto (-> configuration-instance .getConfiguration .getOptions)
        (.setProject project-name)
        (.setProjectType "clojure"))
      (clj4intellij/execute-configuration configuration-instance))

    (wait-console-ui-creation project)

    (testing "user input evaluation"
      (let [repl-content (repl-content project)]

        @(clj4intellij/dispatch-all-until
          (fn [] (str/ends-with? (.getText repl-content) "user> ")))

        (eval-code-on-repl repl-content "(+ 1 1)\n")
        (clj4intellij/dispatch-all)

        (let [content (.getText repl-content)]
          (is (str/ends-with? content "user> (+ 1 1)\n=> 2\nuser> ")))))))

