(ns com.github.clojure-repl.intellij.configuration.repl-type
  (:gen-class
   :init init
   :constructors {[] [String String String com.intellij.openapi.util.NotNullLazyValue]}
   :name com.github.clojure_repl.intellij.configuration.ReplRunConfigurationType
   :extends com.intellij.execution.configurations.ConfigurationTypeBase)
  (:require
   [com.github.clojure-repl.intellij.configuration.factory.local :as config.factory.local]
   [com.github.clojure-repl.intellij.configuration.factory.remote :as config.factory.remote])
  (:import
   [com.intellij.execution.configurations
    ConfigurationFactory]
   [com.intellij.icons AllIcons$Nodes]
   [com.intellij.openapi.util NotNullFactory NotNullLazyValue]))

(set! *warn-on-reflection* true)

(defn -init []
  [["clojure-repl"
    "Clojure REPL"
    "Connect to a local or remote REPL"
    (NotNullLazyValue/createValue
     (reify NotNullFactory
       (create [_]
         AllIcons$Nodes/Console)))] nil])

(defn -getConfigurationFactories [this]
  (into-array ConfigurationFactory [(config.factory.local/configuration-factory this)
                                    (config.factory.remote/configuration-factory this)]))
