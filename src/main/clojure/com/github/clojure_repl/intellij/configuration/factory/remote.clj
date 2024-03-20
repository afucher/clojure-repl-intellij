(ns com.github.clojure-repl.intellij.configuration.factory.remote
  (:require
   [clojure.java.io :as io]
   [com.github.clojure-repl.intellij.configuration.factory.base :as config.factory.base]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.rpl.proxy-plus :refer [proxy+]]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplRemoteRunOptions]
   [com.intellij.execution.configurations
    CommandLineState
    ConfigurationFactory
    ConfigurationType
    RunConfigurationBase]
   [com.intellij.execution.process NopProcessHandler ProcessListener]
   [com.intellij.execution.runners ExecutionEnvironment]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.ui IdeBorderFactory]
   [javax.swing JRadioButton JTextField]))

(set! *warn-on-reflection* true)

(def ^:private options-class ReplRemoteRunOptions)

(defn ^:private read-port-file? []
  (= (-> @db/db* :settings :mode) :file-config))

(defn ^:private setup-process [^ReplRemoteRunOptions options]
  (logger/info "Connecting to nREPL process...")
  (logger/info (str "[setup-process] Host: " (.getNreplHost options)))
  ;TODO: handle when file does not exist
  (when (read-port-file?)
    (let [base-path (.getBasePath ^Project (.getProject ^ReplRemoteRunOptions options))
          repl-file (io/file base-path ".nrepl-port")
          port (slurp repl-file)]
      (swap! db/db* assoc-in [:settings :nrepl-port] (parse-long port))))

  (let [handler (NopProcessHandler.)
        project (.getProject ^ReplRemoteRunOptions options)]
    (swap! config.factory.base/current-repl* assoc :handler handler)
    (.addProcessListener handler
                         (proxy+ [] ProcessListener
                           (startNotified [_ _] (config.factory.base/repl-started project ""))
                           (processWillTerminate [_ _ _] (config.factory.base/repl-disconnected))))
    handler))


(set! *warn-on-reflection* false)

(defn ^:private setup-settings [^RunConfigurationBase configuration-base]
  (logger/info "setup-settings")
  (when-let [host (not-empty (.getNreplHost (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :nrepl-host] host))
  (when-let [nrepl-port (parse-long (.getNreplPort (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :nrepl-port] nrepl-port))
  (when-let [project (not-empty (.getProject (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :project] project))
  (when-let [mode (not-empty (.getMode (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :mode] (keyword mode))))



(defn configuration-factory ^ConfigurationFactory [^ConfigurationType type]
  (proxy [ConfigurationFactory] [type]
    (getId [] "clojure-repl-remote")
    (getName [] "Remote")
    (getOptionsClass [] options-class)
    (createTemplateConfiguration
      ([project _]
       (logger/info "Creating template configuration")
       (.createTemplateConfiguration ^ConfigurationFactory this project))
      ([^Project project]
       (logger/info "Creating template configuration 2")
       (proxy [RunConfigurationBase] [project this "Connect to an existing nREPL process"]
         (getConfigurationEditor []
           (new com.github.clojure_repl.intellij.configuration.editor/SettingsEditor))
         (getState
           ([])
           ([executor ^ExecutionEnvironment env]
            (setup-settings this)
            (proxy [CommandLineState] [env]
              (createConsole [_]
                (config.factory.base/build-console-view project "Connecting to nREPL server..."))
              (startProcess []
                (setup-process (.getOptions this)))))))))))
