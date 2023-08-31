(ns com.github.clojure-repl.intellij.configuration.demo
  (:gen-class
   :init init
   :constructors {[] [String String String com.intellij.openapi.util.NotNullLazyValue]}
   :name com.github.clojure_repl.intellij.configuration.DemoRunConfigurationType
   :extends com.intellij.execution.configurations.SimpleConfigurationType)
  (:require
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.execution.configurations RunConfigurationBase]
   [com.intellij.icons AllIcons$Nodes]
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.util NotNullFactory NotNullLazyValue]))

(set! *warn-on-reflection* true)

(def ID "Clojure REPL")

(defn -init []
  [[ID "Clojure REPL" "Clojure REPL" (NotNullLazyValue/createValue
                                      (reify NotNullFactory
                                        (create [_]
                                          AllIcons$Nodes/Console)))] nil])

(defn -getId [_]
  ID)

(defn -createTemplateConfiguration
  ([this ^Project project _]
   (-createTemplateConfiguration this project))
  ([this ^Project project]
   (proxy+ [project this "Demo"] RunConfigurationBase
           ;; TODO
           #_(getConfigurationEditor []))))

(defn -getHelpTopic [_]
  "Clojure REPL")
