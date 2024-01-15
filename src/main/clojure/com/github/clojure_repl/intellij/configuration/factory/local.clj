(ns com.github.clojure-repl.intellij.configuration.factory.local
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.configuration.factory.base :as config.factory.base]
   [com.github.clojure-repl.intellij.db :as db]
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
   [com.intellij.ui IdeBorderFactory]))

(set! *warn-on-reflection* true)

(def ^:private options-class ReplLocalRunOptions)

(defn ^:private repl-started-initial-text [command]
  (str "\n;; Startup: " command))

(defn ^:private process-output->nrepl-uri [text]
  (or (when-let [[_ port] (re-find #"nREPL server started on port (\d+)" text)]
        ["localhost" (parse-long port)])
      (when-let [[_ host port] (re-find #"Started nREPL server at (\w+):(\d+)" text)]
        [host (parse-long port)])))

(defn ^:private project->project-type [^Project project]
  (let [project-path (.getBasePath project)]
    (some #(case %
             "project.clj" :lein
             "deps.edn" :clojure
             "bb.edn" :babashka
             "shadow-cljs.edn" :shadow-cljs
             "build.boot" :boot
             "nbb.edn" :nbb
             ("build.gradle" "build.gradle.kts") :gradle
             nil)
          (.list (io/file project-path)))))

(def ^:private project-type->command
  {:lein "lein"
   :clojure "clojure"
   :babashka "bb"
   :shadow-cljs "npx shadow-cljs"
   :boot "boot"
   :nbb "nbb"
   :gradle "./gradlew"})

(def ^:private project-type->parameters
  {:lein "repl :headless :host localhost"
   :clojure "-Sdeps {:deps{nrepl/nrepl{:mvn/version\"1.1.0\"}}} -M -m nrepl.cmdline"
   :babashka "nrepl-server localhost:0"
   :shadow-cljs "server"
   :boot "repl -s -b localhost wait"
   :nbb "nrepl-server"
   :gradle "clojureRepl"})

(defn ^:private project->repl-start-command [^Project project]
  (let [project-type (project->project-type project)
        command (project-type->command project-type)
        parameters (project-type->parameters project-type)]
    ;; TODO Add support for global options along with parameters like aliases
    ;; and dependencies injection, check how cider does for more details
    (format "%s %s" command parameters)))

(defn ^:private setup-process [project command]
  (let [command-line (GeneralCommandLine. ^java.util.List (string/split command #" "))
        handler (.createColoredProcessHandler (ProcessHandlerFactory/getInstance)
                                              command-line)]
    (swap! config.factory.base/current-repl* assoc :handler handler)
    (.addProcessListener handler
                         (proxy+ [] ProcessListener
                           (onTextAvailable [_ ^ProcessEvent event _key]
                             (if-let [[host port] (process-output->nrepl-uri (.getText event))]
                               (do
                                 (swap! db/db* assoc-in [:settings :nrepl-host] host)
                                 (swap! db/db* assoc-in [:settings :nrepl-port] port)
                                 (config.factory.base/repl-started project (repl-started-initial-text command)))
                               (ui.repl/append-text (:console @config.factory.base/current-repl*) (.getText event))))
                           (processWillTerminate [_ _ _] (config.factory.base/repl-disconnected))))
    (logger/info "Starting nREPL process:" command)
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
(defn ^:private apply-editor-to [^RunConfigurationBase configuration-base]
  (let [editor @config.factory.base/editor-view*
        project (seesaw/text (seesaw/select editor [:#project]))]
    (.setProject (.getOptions configuration-base) project)))

(defn ^:private setup-settings [^RunConfigurationBase configuration-base]
  (when-let [project (not-empty (.getProject (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :project] project)))
(set! *warn-on-reflection* true)

(defn ^:private reset-editor-from-settings []
  (let [editor @config.factory.base/editor-view*
        settings (:settings @db/db*)]
    (seesaw/text! (seesaw/select editor [:#project]) (:project settings))))

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
               (let [editor-view (build-editor-view)]
                 (reset! config.factory.base/editor-view* editor-view)
                 editor-view))
             (applyEditorTo [_ configuration-base]
               (apply-editor-to configuration-base))

             (resetEditorFrom [_ configuration-base]
               (setup-settings configuration-base)
               (reset-editor-from-settings))))

         (getState
           ([])
           ([executor ^ExecutionEnvironment env]
            (let [command (project->repl-start-command project)]
              (setup-settings this)
              (proxy [CommandLineState] [env]
                (createConsole [_]
                  (config.factory.base/build-console-view project "Starting nREPL server via: "))
                (startProcess []
                  (setup-process project command)))))))))))
