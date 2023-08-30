(ns com.github.clojure-repl.intellij.configuration.demo-factory
  (:gen-class
    :name com.github.clojure_repl.intellij.configuration.DemoConfigurationFactory
    :extends com.intellij.execution.configurations.ConfigurationFactory)
  (:require [com.github.clojure-repl.intellij.configuration.demo :as demo]))

(set! *warn-on-reflection* true)

(defn -getId [_]
  demo/ID)

