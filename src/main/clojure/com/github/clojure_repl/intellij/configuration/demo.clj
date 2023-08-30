(ns com.github.clojure-repl.intellij.configuration.demo
  (:gen-class
    :post-init post-init
    :init init
    :name com.github.clojure_repl.intellij.configuration.DemoRunConfigurationType
    :constructors {[] [String String String com.intellij.openapi.util.NotNullLazyValue]}
    :extends com.intellij.execution.configurations.ConfigurationTypeBase)
  (:import (com.intellij.execution.configurations ConfigurationFactory)
           (com.intellij.icons AllIcons$Nodes)
           (com.intellij.openapi.util NotNullLazyValue))
  (:require [com.rpl.proxy-plus :refer [proxy+]]))

(set! *warn-on-reflection* false)

(def ID "DemoRunConfiguration")

(defn -init []
  [[ID "Demo" "Demo run  configuration type" (NotNullLazyValue/createValue (fn [] AllIcons$Nodes/Console ))] nil])

(defn -post-init [this _]
  (.addFactory this (proxy+
                      [this]
                      ConfigurationFactory
                      (getId [] ID))))




(defn -getId [_]
  ID)

