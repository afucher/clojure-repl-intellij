(ns com.github.clojure-repl.intellij.extension.init-db-startup
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.InitDBStartup
   :implements [com.intellij.openapi.startup.ProjectActivity
                com.intellij.openapi.project.DumbAware])
  (:require
   [com.github.clojure-repl.intellij.db :as db])
  (:import
   [com.intellij.openapi.project Project]
   [kotlinx.coroutines CoroutineScope]))

(set! *warn-on-reflection* true)

(defn -execute [_this ^Project project ^CoroutineScope _]
  (db/init-db-for-project project))
