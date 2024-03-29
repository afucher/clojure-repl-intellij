(ns com.github.clojure-repl.intellij.extension.startup
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.Startup
   :implements [com.intellij.openapi.startup.StartupActivity
                com.intellij.openapi.project.DumbAware])
  (:require
   [com.github.clojure-repl.intellij.db :as db])
  (:import
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* true)

(defn -runActivity [_this ^Project project]
  (db/init-db-for-project project))
