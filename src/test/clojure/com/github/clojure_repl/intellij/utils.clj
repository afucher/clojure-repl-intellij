(ns com.github.clojure-repl.intellij.utils
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.test :as clj4intellij.test]
   [seesaw.core :as seesaw])
  (:import
   [com.intellij.execution RunManager]
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* true)

(defn stop-all-configurations
  "Stops all running configurations for a given project"
  [^Project project]
  (let [run-manager (RunManager/getInstance project)
        configurations (.getAllConfigurations run-manager)]
    (doseq [config configurations]
      (when (.isRunning config)
        (.stop config)))))

(defn repl-content [project]
  (-> project
      (db/get-in [:console :ui])
      (seesaw/select [:#repl-content])))

(defn ensure-repl-editor
  "Ensure the editor was created in the UI thread"
  [project]
  (let [repl-content (repl-content project)]
    @(app-manager/invoke-later!
      {:invoke-fn (fn []
                    (.addNotify repl-content)
                    (.getEditor repl-content true))})))

(defn wait-for-editor
  "Waits for the editor to be created, polling up to `timeout-ms`."
  [project & {:keys [timeout-ms interval-ms] :or {timeout-ms 5000 interval-ms 50}}]
  (let [start (System/currentTimeMillis)]
    (loop []
      (ensure-repl-editor project)
      (when-not (.getEditor (repl-content project))
        (if (< (- (System/currentTimeMillis) start) timeout-ms)
          (do (Thread/sleep interval-ms)
              (recur))
          (throw (ex-info "Timeout waiting for editor creation" {})))))))

(defn wait-console-ui-creation
  "Waits until the console UI is set in the db*, then ensures the editor is created"
  [project]
  @(clj4intellij.test/dispatch-all-until
    {:cond-fn (fn [] (-> project
                         (db/get-in [:console :ui])))})
  (wait-for-editor project))
