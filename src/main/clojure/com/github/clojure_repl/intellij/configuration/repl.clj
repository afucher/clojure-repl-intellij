(ns com.github.clojure-repl.intellij.configuration.repl
  (:gen-class
   :init init
   :constructors {[] [String String String com.intellij.openapi.util.NotNullLazyValue]}
   :name com.github.clojure_repl.intellij.configuration.ReplRunConfigurationType
   :extends com.intellij.execution.configurations.ConfigurationTypeBase)
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.rpl.proxy-plus :refer [proxy+]]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplLocalRunOptions ReplRemoteRunOptions]
   [com.intellij.execution.configurations
    CommandLineState
    ConfigurationFactory
    ConfigurationType
    GeneralCommandLine
    RunConfigurationBase]
   [com.intellij.execution.process
    NopProcessHandler
    ProcessEvent
    ProcessHandlerFactory
    ProcessListener]
   [com.intellij.execution.runners ExecutionEnvironment]
   [com.intellij.execution.ui ConsoleView]
   [com.intellij.icons AllIcons$Nodes]
   [com.intellij.ide.plugins PluginManagerCore]
   [com.intellij.openapi.actionSystem AnAction]
   [com.intellij.openapi.extensions PluginId]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.openapi.util NotNullFactory NotNullLazyValue]
   [com.intellij.ui IdeBorderFactory]))

(set! *warn-on-reflection* false)

(def initial-current-repl {:handler nil
                           :console nil})
(defonce current-repl* (atom initial-current-repl))
(defonce editor-view* (atom nil))

(def repl-remote-run-options-class ReplRemoteRunOptions)
(def repl-local-run-options-class ReplLocalRunOptions)

(defn ^:private base-initial-repl-text []
  (let [{:keys [clojure java nrepl]} (-> @db/db* :current-nrepl :versions)]
    (str (format ";; Connected to nREPL server - nrepl://%s:%s\n"
                 (-> @db/db* :settings :nrepl-host)
                 (-> @db/db* :settings :nrepl-port))
         (format ";; Clojure REPL Intellij %s\n"
                 (.getVersion
                  (PluginManagerCore/getPlugin (PluginId/getId "com.github.clojure-repl"))))
         (format ";; Clojure %s, Java %s, nREPL %s"
                 (:version-string clojure)
                 (:version-string java)
                 (:version-string nrepl)))))

(def ^:private remote-repl-started-initial-text base-initial-repl-text)

(defn ^:private local-repl-started-initial-text [command]
  (str (base-initial-repl-text) "\n"
       ";; Startup: " command))

(defn ^:private repl-disconnected []
  (ui.repl/close-console (:console @current-repl*))
  (reset! current-repl* initial-current-repl)
  (swap! db/db* assoc :current-nrepl nil))

(defn ^:private repl-started [project initial-text]
  (nrepl/clone-session)
  (nrepl/eval {:project project :code "*ns*"})
  (let [description (nrepl/describe)]
    (swap! db/db* assoc-in [:current-nrepl :ops] (:ops description))
    (swap! db/db* assoc-in [:current-nrepl :versions] (:versions description))
    (ui.repl/set-initial-text (:console @current-repl*) initial-text)))

(defn ^:private build-console-view [project loading-text]
  (reset! current-repl* initial-current-repl)
  (swap! current-repl* assoc :console (ui.repl/build-console
                                       {:on-eval (fn [code]
                                                   (nrepl/eval {:project project :code code}))}))
  (ui.repl/append-text (:console @current-repl*) loading-text)
  (proxy+ [] ConsoleView
    (getComponent [_] (:console @current-repl*))
    (getPreferredFocusableComponent [_] (:console @current-repl*))
    (dispose [_])
    (print [_ _ _])
    (clear [_])
    (scrollTo [_ _])
    (attachToProcess [_ _])
    (setOutputPaused [_ _])
    (isOutputPaused [_] false)
    (hasDeferredOutput [_] false)
    (performWhenNoDeferredOutput [_ _])
    (setHelpId [_ _])
    (addMessageFilter [_ _])
    (printHyperlink [_ _ _])
    (getContentSize [_] 0)
    (canPause [_] false)
    (createConsoleActions [_] (into-array AnAction []))
    (allowHeavyFilters [_])))

(defn ^:private remote-setup-process [project]
  (logger/info "Connecting to nREPL process...")
  (let [handler (NopProcessHandler.)]
    (swap! current-repl* assoc :handler handler)
    (.addProcessListener handler
                         (proxy+ [] ProcessListener
                           (startNotified [_ _] (repl-started project (remote-repl-started-initial-text)))
                           (processWillTerminate [_ _ _] (repl-disconnected))))
    handler))

(defn ^:private remote-build-editor-view []
  (mig/mig-panel
   :border (IdeBorderFactory/createTitledBorder "nREPL connection")
   :items [[(seesaw/label "Host") ""]
           [(seesaw/text :id :nrepl-host
                         :columns 20) "wrap"]
           [(seesaw/label "Port") ""]
           [(seesaw/text :id :nrepl-port
                         :columns 8) "wrap"]
           [(seesaw/label "Project") ""]
           [(seesaw/combobox :id    :project
                             :model (->> (ProjectManager/getInstance)
                                         .getOpenProjects
                                         (map #(.getName ^Project %)))) "wrap"]]))

(defn ^:private remote-apply-editor-to [^RunConfigurationBase configuration-base]
  (let [editor @editor-view*
        host (seesaw/text (seesaw/select editor [:#nrepl-host]))
        project (seesaw/text (seesaw/select editor [:#project]))]
    (swap! db/db* assoc-in [:settings :nrepl-host] host)
    (.setNreplHost (.getOptions configuration-base) host)
    (when-let [nrepl-port (parse-long (seesaw/text (seesaw/select editor [:#nrepl-port])))]
      (swap! db/db* assoc-in [:settings :nrepl-port] nrepl-port)
      (.setNreplPort (.getOptions configuration-base) (str nrepl-port))
      (swap! db/db* assoc-in [:settings :project] host)
      (.setProject (.getOptions configuration-base) project))))

(defn ^:private remote-setup-settings [^RunConfigurationBase configuration-base]
  (when-let [host (not-empty (.getNreplHost (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :nrepl-host] host))
  (when-let [nrepl-port (parse-long (.getNreplPort (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :nrepl-port] nrepl-port))
  (when-let [project (not-empty (.getProject (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :project] project)))

(defn ^:private remote-reset-editor-from-settings []
  (let [editor @editor-view*
        settings (:settings @db/db*)]
    (seesaw/text! (seesaw/select editor [:#nrepl-host]) (:nrepl-host settings))
    (seesaw/text! (seesaw/select editor [:#nrepl-port]) (:nrepl-port settings))
    (seesaw/text! (seesaw/select editor [:#project]) (:project settings))))

(defn ^:private remote-repl-configuration-type ^ConfigurationFactory [^ConfigurationType type]
  (proxy [ConfigurationFactory] [type]
    (getId [] "clojure-repl-remote")
    (getName [] "Remote")
    (getOptionsClass [] repl-remote-run-options-class)
    (createTemplateConfiguration
      ([project _]
       (.createTemplateConfiguration this project))
      ([^Project project]
       (proxy [RunConfigurationBase] [project this "Connect to an existing nREPL process"]
         (getConfigurationEditor []
           (proxy+ [] com.intellij.openapi.options.SettingsEditor
             (createEditor [_]
               (let [editor-view (remote-build-editor-view)]
                 (reset! editor-view* editor-view)
                 editor-view))
             (applyEditorTo [_ configuration-base]
               (remote-apply-editor-to configuration-base))

             (resetEditorFrom [_ configuration-base]
               (remote-setup-settings configuration-base)
               (remote-reset-editor-from-settings))))

         (getState
           ([])
           ([executor ^ExecutionEnvironment env]
            (remote-setup-settings this)
            (proxy [CommandLineState] [env]
              (createConsole [_]
                (build-console-view project "Connecting to nREPL server..."))
              (startProcess []
                (remote-setup-process project))))))))))

;; [nREPL] Starting server via lein update-in :dependencies conj \[nrepl/nrepl\ \"1.0.0\"\]
;; -- update-in :dependencies conj \[refactor-nrepl/refactor-nrepl\ \"3.6.0\"\]
;; -- update-in :plugins conj \[refactor-nrepl/refactor-nrepl\ \"3.6.0\"\]
;; -- update-in :plugins conj \[cider/cider-nrepl\ \"0.30.0\"\]
;; -- repl :headless :host localhost

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
   :clojure ""
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

(defn ^:private local-setup-process [project]
  (let [command (project->repl-start-command project)
        command-line (GeneralCommandLine. (string/split command #" "))
        handler (.createColoredProcessHandler (ProcessHandlerFactory/getInstance)
                                              command-line)]
    (swap! current-repl* assoc :handler handler)
    (.addProcessListener handler
                         (proxy+ [] ProcessListener
                           (onTextAvailable [_ ^ProcessEvent event _key]
                             (when-let [[host port] (process-output->nrepl-uri (.getText event))]
                               (swap! db/db* assoc-in [:settings :nrepl-host] host)
                               (swap! db/db* assoc-in [:settings :nrepl-port] port)
                               (repl-started project (local-repl-started-initial-text command))))
                           (processWillTerminate [_ _ _] (repl-disconnected))))
    (logger/info "Starting nREPL process:" command)
    handler))

(defn ^:private local-build-editor-view []
  (mig/mig-panel
   :border (IdeBorderFactory/createTitledBorder "nREPL connection")
   :items [[(seesaw/label "Project") ""]
           [(seesaw/combobox :id    :project
                             :model (->> (ProjectManager/getInstance)
                                         .getOpenProjects
                                         (map #(.getName ^Project %)))) "wrap"]]))

(defn ^:private local-apply-editor-to [^RunConfigurationBase configuration-base]
  (let [editor @editor-view*
        project (seesaw/text (seesaw/select editor [:#project]))]
    (.setProject (.getOptions configuration-base) project)))

(defn ^:private local-setup-settings [^RunConfigurationBase configuration-base]
  (when-let [project (not-empty (.getProject (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :project] project)))

(defn ^:private local-reset-editor-from-settings []
  (let [editor @editor-view*
        settings (:settings @db/db*)]
    (seesaw/text! (seesaw/select editor [:#project]) (:project settings))))

(defn ^:private local-repl-configuration-type ^ConfigurationFactory [^ConfigurationType type]
  (proxy [ConfigurationFactory] [type]
    (getId [] "clojure-repl-local")
    (getName [] "Local")
    (getOptionsClass [] repl-local-run-options-class)
    (createTemplateConfiguration
      ([project _]
       (.createTemplateConfiguration this project))
      ([^Project project]
       (proxy [RunConfigurationBase] [project this "Start a local nREPL process"]
         (getConfigurationEditor []
           (proxy+ [] com.intellij.openapi.options.SettingsEditor
             (createEditor [_]
               (let [editor-view (local-build-editor-view)]
                 (reset! editor-view* editor-view)
                 editor-view))
             (applyEditorTo [_ configuration-base]
               (local-apply-editor-to configuration-base))

             (resetEditorFrom [_ configuration-base]
               (local-setup-settings configuration-base)
               (local-reset-editor-from-settings))))

         (getState
           ([])
           ([executor ^ExecutionEnvironment env]
            (local-setup-settings this)
            (proxy [CommandLineState] [env]
              (createConsole [_]
                (build-console-view project "Starting nREPL server via: "))
              (startProcess []
                (local-setup-process project))))))))))

(defn -init []
  [["clojure-repl"
    "Clojure REPL"
    "Connect to a local or remote REPL"
    (NotNullLazyValue/createValue
     (reify NotNullFactory
       (create [_]
         AllIcons$Nodes/Console)))] nil])

(defn -getConfigurationFactories [this]
  (into-array ConfigurationFactory [(local-repl-configuration-type this)
                                    (remote-repl-configuration-type this)]))
