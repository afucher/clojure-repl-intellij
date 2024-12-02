(ns com.github.clojure-repl.intellij.configuration.factory.local
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.configuration.factory.base :as config.factory.base]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.interop :as interop]
   [com.github.clojure-repl.intellij.project :as project]
   [com.github.clojure-repl.intellij.repl-command :as repl-command]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplLocalRunOptions]
   [com.intellij.execution.configurations
    CommandLineState
    ConfigurationFactory
    ConfigurationType
    GeneralCommandLine
    RunConfigurationBase]
   [com.intellij.execution.process ColoredProcessHandler ProcessEvent ProcessListener]
   [com.intellij.execution.runners ExecutionEnvironment]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.util.io BaseOutputReader$Options]
   [java.nio.charset Charset]))

(set! *warn-on-reflection* false)
(defn ^:private project-name [configuration] (.getProject (.getOptions configuration)))
(defn ^:private project-type [configuration] (keyword (.getProjectType (.getOptions configuration))))
(defn ^:private aliases [configuration] (seq (.getAliases (.getOptions configuration))))
(defn ^:private env-vars [configuration]
  (into {}
        (mapv #(string/split % #"=")
              (seq (.getEnvVars (.getOptions configuration))))))
(set! *warn-on-reflection* true)

(def ^:private options-class ReplLocalRunOptions)

(defn ^:private repl-started-initial-text [command]
  (str "\n;; Startup: " command))

(defn ^:private process-output->nrepl-uri [text]
  (or (when-let [[_ port] (re-find #"nREPL server started on port (\d+)" text)]
        ["localhost" (parse-long port)])
      (when-let [[_ host port] (re-find #"Started nREPL server at (\w+):(\d+)" text)]
        [host (parse-long port)])))

(defn ^:private setup-process [^Project project command env-vars]
  (let [command-str (string/join " " command)
        command-line  (doto (GeneralCommandLine. ^java.util.List command)
                        (.setCharset (Charset/forName "UTF-8"))
                        (.setWorkDirectory (.getBasePath project))
                        (.withEnvironment env-vars))
        handler (proxy [ColoredProcessHandler] [command-line]
                  (readerOptions []
                    (BaseOutputReader$Options/forMostlySilentProcess)))]
    (db/assoc-in! project [:console :process-handler] handler)
    (.addProcessListener handler
                         (proxy+ [] ProcessListener
                           (onTextAvailable [_ ^ProcessEvent event _key]
                             (if-let [[host port] (process-output->nrepl-uri (.getText event))]
                               (do
                                 (db/assoc-in! project [:current-nrepl :nrepl-host] host)
                                 (db/assoc-in! project [:current-nrepl :nrepl-port] port)
                                 (config.factory.base/repl-started project (repl-started-initial-text command-str)))
                               (ui.repl/append-text (db/get-in project [:console :ui]) (.getText event))))
                           (processWillTerminate [_ _ _] (config.factory.base/repl-disconnected project))))
    (logger/info "Starting nREPL process:" command-str)
    handler))

(defn configuration-factory ^ConfigurationFactory [^ConfigurationType type]
  (proxy [ConfigurationFactory] [type]
    (getId [] "clojure-repl-local")
    (getName [] "Local")
    (getOptionsClass [] options-class)
    (createTemplateConfiguration
      ([project _]
       (.createTemplateConfiguration ^ConfigurationFactory this project))
      ([^Project project]
       (proxy [RunConfigurationBase] [project this "Start a local nREPL process"]
         (getConfigurationEditor []
           (interop/new-class "com.github.clojure_repl.intellij.configuration.settings_editor.Local"))

         (getState
           ([])
           ([executor ^ExecutionEnvironment env]
            (let [project ^Project (->> (ProjectManager/getInstance)
                                        .getOpenProjects
                                        (filter #(= (project-name this) (.getName ^Project %)))
                                        first)
                  config-project-type (project-type this)
                  project-type (if (contains? project/known-project-types config-project-type)
                                 config-project-type
                                 (project/project->project-type project))
                  command (repl-command/project->repl-start-command project-type (aliases this))
                  env-vars (env-vars this)]
              (proxy [CommandLineState] [env]
                (createConsole [_]
                  (config.factory.base/build-console-view project "Starting nREPL server via: "))
                (startProcess []
                  (setup-process project command env-vars)))))))))))
