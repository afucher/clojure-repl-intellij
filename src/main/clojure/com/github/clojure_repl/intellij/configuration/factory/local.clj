(ns com.github.clojure-repl.intellij.configuration.factory.local
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.configuration.factory.base :as config.factory.base]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.repl-command :as repl-command]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.rpl.proxy-plus :refer [proxy+]]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplLocalRunOptions]
   [com.intellij.execution.configurations
    CommandLineState
    ConfigurationFactory
    ConfigurationType
    GeneralCommandLine
    RunConfigurationBase]
   [com.intellij.execution.process ProcessEvent ProcessHandlerFactory ProcessListener]
   [com.intellij.execution.runners ExecutionEnvironment]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.ui IdeBorderFactory]
   [java.nio.charset Charset]))

(set! *warn-on-reflection* true)

(def ^:private options-class ReplLocalRunOptions)

(defn ^:private repl-started-initial-text [command]
  (str "\n;; Startup: " command))

(defn ^:private process-output->nrepl-uri [text]
  (or (when-let [[_ port] (re-find #"nREPL server started on port (\d+)" text)]
        ["localhost" (parse-long port)])
      (when-let [[_ host port] (re-find #"Started nREPL server at (\w+):(\d+)" text)]
        [host (parse-long port)])))

(defn ^:private setup-process [^Project project command]
  (let [command-str (string/join " " command)
        command-line  (doto (GeneralCommandLine. ^java.util.List command)
                        (.setCharset (Charset/forName "UTF-8"))
                        (.setWorkDirectory (.getBasePath project)))
        handler (.createColoredProcessHandler (ProcessHandlerFactory/getInstance)
                                              command-line)]
    (db/assoc-in project [:console :process-handler] handler)
    (.addProcessListener handler
                         (proxy+ [] ProcessListener
                           (onTextAvailable [_ ^ProcessEvent event _key]
                             (if-let [[host port] (process-output->nrepl-uri (.getText event))]
                               (do
                                 (db/assoc-in project [:settings :nrepl-host] host)
                                 (db/assoc-in project [:settings :nrepl-port] port)
                                 (config.factory.base/repl-started project (repl-started-initial-text command-str)))
                               (ui.repl/append-text (db/get-in project [:console :ui]) (.getText event))))
                           (processWillTerminate [_ _ _] (config.factory.base/repl-disconnected project))))
    (logger/info "Starting nREPL process:" command-str)
    handler))

(defn ^:private build-editor-view []
  (mig/mig-panel
   :border (IdeBorderFactory/createTitledBorder "nREPL connection")
   :items [[(seesaw/label "Project") ""]
           [(seesaw/combobox :id    :project
                             :model (->> (ProjectManager/getInstance)
                                         .getOpenProjects
                                         (map #(.getName ^Project %)))) "wrap"]]))

(set! *warn-on-reflection* false)
(defn ^:private apply-editor-to [project ^RunConfigurationBase configuration-base]
  (let [ui (db/get-in project [:run-configuration :ui])
        project (seesaw/text (seesaw/select ui [:#project]))]
    (.setProject (.getOptions configuration-base) project)))

(defn ^:private setup-settings [project ^RunConfigurationBase configuration-base]
  (when-let [project-path (not-empty (.getProject (.getOptions configuration-base)))]
    (db/assoc-in project [:settings :project] project-path)))
(set! *warn-on-reflection* true)

(defn ^:private reset-editor-from-settings [project]
  (let [ui (db/get-in project [:run-configuration :ui])
        settings (db/get-in project [:settings])]
    (seesaw/text! (seesaw/select ui [:#project]) (:project settings))))

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
           (proxy+ [] com.intellij.openapi.options.SettingsEditor
             (createEditor [_]
               (let [editor-ui (build-editor-view)]
                 (db/assoc-in project [:run-configuration :ui] editor-ui)
                 editor-ui))
             (applyEditorTo [_ configuration-base]
               (apply-editor-to project configuration-base))

             (resetEditorFrom [_ configuration-base]
               (setup-settings project configuration-base)
               (reset-editor-from-settings project))))

         (getState
           ([])
           ([executor ^ExecutionEnvironment env]
            (let [command (repl-command/project->repl-start-command (.getBasePath project))]
              (setup-settings project this)
              (proxy [CommandLineState] [env]
                (createConsole [_]
                  (config.factory.base/build-console-view project "Starting nREPL server via: "))
                (startProcess []
                  (setup-process project command)))))))))))
