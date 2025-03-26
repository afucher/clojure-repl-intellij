(ns com.github.clojure-repl.intellij.foo-test
  (:require
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [com.github.clojure-repl.intellij.clj4intellij :as clj4intellij]
   [com.github.clojure-repl.intellij.configuration.factory.local :as config.factory.local]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [seesaw.core :as seesaw])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplRunConfigurationType]
   [com.intellij.execution ExecutionManager ProgramRunnerUtil RunManager]
   [com.intellij.execution.executors DefaultRunExecutor]
   [com.intellij.openapi.util ThrowableComputable]
   [com.intellij.testFramework EditorTestUtil EdtTestUtil]
   [java.awt.event KeyEvent]))

(set! *warn-on-reflection* true)

(deftest foo-test
  (let [fixture (clj4intellij/setup)
        deps-file (.createFile fixture "deps.edn" "{}")]
    (is deps-file)
    (clj4intellij/write-command-action
     (.getProject fixture)
     (fn [] (.openFileInEditor fixture deps-file)))
    (is (.getEditor fixture))
    (let [project (.getProject fixture)
          run-manager (RunManager/getInstance project)
          configuration (config.factory.local/configuration-factory (ReplRunConfigurationType.))
          configuration-instance (.createConfiguration run-manager "Local REPL" configuration)
          config-base (.getConfiguration configuration-instance)
          options (.getOptions config-base)]
      (doto options
        (.setProject "clojure.core")
        (.setProjectType "clojure"))

      (let [execution-manager (ExecutionManager/getInstance project)
            _ (ProgramRunnerUtil/executeConfiguration configuration-instance
                                                      (DefaultRunExecutor/getRunExecutorInstance))]

        (clj4intellij/dispatchAll)
        (clojure.pprint/pprint (.getRunningProcesses execution-manager))))

    (let [console (-> @db/db* :projects first second :console :ui)
          repl-content (seesaw/select console [:#repl-content])
          #_#_editor (.getEditor repl-content)]
      @(app-manager/invoke-later!
        {:invoke-fn (fn [] (println "invoke-later")
                      (.addNotify repl-content)
                      (println "editor> " (.getEditor repl-content true)))})
      @(clj4intellij/dispatch-all-until
        (fn [] (str/ends-with? (.getText repl-content) "user> ")))

      (let [text "(+ 1 1)\n"
            component (.getContentComponent (.getEditor repl-content))
            key-enter-event (KeyEvent. component KeyEvent/KEY_PRESSED (System/currentTimeMillis) 0 KeyEvent/VK_ENTER Character/MIN_VALUE)]
        (EdtTestUtil/runInEdtAndGet
         (reify ThrowableComputable
           (compute [_]
             (doseq [char text]
               (EditorTestUtil/performTypingAction (.getEditor repl-content) char))
             (clojure.pprint/pprint (.getText repl-content))
             (.dispatchEvent component key-enter-event)
             nil))))

      (clj4intellij/dispatchAll)

      (let [content (.getText repl-content)]
        (is (str/ends-with? content "user> (+ 1 1)\n=> 2\nuser> "))))



      ;; (println '_______________ (.getExitCode (.getProcessHandler (.getSelectedContent (RunContentManager/getInstance project)))))

    #_(is false)

    #_()))

