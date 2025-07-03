(ns com.github.clojure-repl.intellij.configuration.factory.remote
  (:require
   [clojure.java.io :as io]
   [com.github.clojure-repl.intellij.configuration.factory.base :as config.factory.base]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.interop :as interop]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplRemoteRunOptions]
   [com.intellij.execution.configurations
    CommandLineState
    ConfigurationFactory
    ConfigurationType
    RunConfigurationBase]
   [com.intellij.execution.process NopProcessHandler ProcessListener]
   [com.intellij.execution.runners ExecutionEnvironment]
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* false)
(defn ^:private nrepl-host [configuration] (.getNreplHost (.getOptions configuration)))
(defn ^:private nrepl-port [configuration] (.getNreplPort (.getOptions configuration)))
(defn ^:private mode [configuration] (keyword (.getMode (.getOptions configuration))))
(set! *warn-on-reflection* true)

(def ^:private options-class ReplRemoteRunOptions)

(defn ^:private read-port-file? [configuration]
  (= (mode configuration) :file-config))

(defn ^:private setup-process [configuration project]
  (logger/info "Connecting to nREPL process...")
  (let [host (nrepl-host configuration)
        port (nrepl-port configuration)]
    (db/assoc-in! project [:current-nrepl :nrepl-host] host)
    (db/assoc-in! project [:current-nrepl :nrepl-port] (parse-long port))
    (when (read-port-file? configuration)
      (let [base-path (.getBasePath ^Project project)
            repl-file (io/file base-path ".nrepl-port")
            ;TODO: handle when file does not exist
            port (slurp repl-file)]
        (db/assoc-in! project [:current-nrepl :nrepl-port] (parse-long port))))

    (let [handler (NopProcessHandler.)]
      (db/assoc-in! project [:console :process-handler] handler)
      (.addProcessListener handler
                           (proxy+ [] ProcessListener
                             (startNotified [_ _] (config.factory.base/repl-started project ""))
                             (processWillTerminate [_ _ _] (config.factory.base/repl-disconnected project))))
      handler)))

(defn configuration-factory ^ConfigurationFactory [^ConfigurationType type]
  (proxy [ConfigurationFactory] [type]
    (getId [] "clojure-repl-remote")
    (getName [] "Remote")
    (getOptionsClass [] options-class)
    (createTemplateConfiguration
      ([project _]
       (.createTemplateConfiguration ^ConfigurationFactory this project))
      ([^Project project]
       (proxy [RunConfigurationBase] [project this "Connect to an existing nREPL process"]
         (getConfigurationEditor []
           (interop/new-class "com.github.clojure_repl.intellij.configuration.settings_editor.Remote"))

         (getState
           ([])
           ([executor ^ExecutionEnvironment env]
            (proxy+ [env] CommandLineState
              (createConsole [_ _]
                (config.factory.base/build-console-view project "Connecting to nREPL server...\n"))
              (startProcess [_]
                (setup-process this project))))))))))
