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

(defn ^:private read-port-file? [project]
  (= (db/get-in project [:settings :mode]) :file-config))

(defn ^:private setup-process [project]
  (logger/info "Connecting to nREPL process...")
  ;TODO: handle when file does not exist
  (when (read-port-file? project)
    (let [base-path (.getBasePath ^Project project)
          repl-file (io/file base-path ".nrepl-port")
          port (slurp repl-file)]
      (db/assoc-in project [:settings :nrepl-port] (parse-long port))))

  (let [handler (NopProcessHandler.)]
    (db/assoc-in project [:console :process-handler] handler)
    (.addProcessListener handler
                         (proxy+ [] ProcessListener
                           (startNotified [_ _] (config.factory.base/repl-started project ""))
                           (processWillTerminate [_ _ _] (config.factory.base/repl-disconnected project))))
    handler))

(defn manual? [editor]
  (.isSelected ^JRadioButton (seesaw/select editor [:#manual])))

(defn mode-id-key [repl-mode]
  (->> (seesaw/selection repl-mode)
       (seesaw/id-of)))

(defn ^:private build-editor-ui []
  (let [repl-mode-group (seesaw/button-group)
        panel (mig/mig-panel
               :border (IdeBorderFactory/createTitledBorder "NREPL connection")
               :items [[(seesaw/radio :text "Manual"
                                      :id :manual
                                      :group repl-mode-group
                                      :mnemonic \M
                                      :selected? true) "wrap"]
                       [(seesaw/label "Host") ""]
                       [(seesaw/text :id :nrepl-host
                                     :columns 20) "wrap"]
                       [(seesaw/label "Port") ""]
                       [(seesaw/text :id :nrepl-port
                                     :columns 8) "wrap"]
                       [(seesaw/radio :text "Read from repl file"
                                      :id :repl-file
                                      :group repl-mode-group
                                      :mnemonic \R) "wrap"]
                       [(seesaw/label "Project") ""]
                       [(seesaw/combobox :id :project
                                         :model (->> (ProjectManager/getInstance)
                                                     .getOpenProjects
                                                     (map #(.getName ^Project %)))) "wrap"]])]
    (seesaw/listen repl-mode-group :action
                   (fn [_e]
                     (let [mode-key (mode-id-key repl-mode-group)
                           manual? (= mode-key :manual)]
                       (.setEnabled ^JTextField (seesaw/select panel [:#nrepl-host]) manual?)
                       (.setEnabled ^JTextField (seesaw/select panel [:#nrepl-port]) manual?))))

    panel))

(set! *warn-on-reflection* false)
(defn ^:private apply-editor-to [^RunConfigurationBase configuration-base project]
  (let [ui (db/get-in project [:run-configuration :ui])
        host (seesaw/text (seesaw/select ui [:#nrepl-host]))
        project-path (seesaw/text (seesaw/select ui [:#project]))
        mode (if (manual? ui) :manual-config :file-config)]
    (db/assoc-in project [:settings :nrepl-host] host)
    (.setNreplHost (.getOptions configuration-base) host)
    (db/assoc-in project [:settings :mode] mode)
    (.setMode (.getOptions configuration-base) (name mode))
    (when-let [nrepl-port (parse-long (seesaw/text (seesaw/select ui [:#nrepl-port])))]
      (db/assoc-in project [:settings :nrepl-port] nrepl-port)
      (.setNreplPort (.getOptions configuration-base) (str nrepl-port))
      (db/assoc-in project [:settings :project] host)
      (.setProject (.getOptions configuration-base) project-path))))

(defn ^:private setup-settings [^RunConfigurationBase configuration-base project]
  (when-let [host (not-empty (.getNreplHost (.getOptions configuration-base)))]
    (db/assoc-in project [:settings :nrepl-host] host))
  (when-let [nrepl-port (parse-long (.getNreplPort (.getOptions configuration-base)))]
    (db/assoc-in project [:settings :nrepl-port] nrepl-port))
  (when-let [project-path (not-empty (.getProject (.getOptions configuration-base)))]
    (db/assoc-in project [:settings :project] project-path))
  (when-let [mode (not-empty (.getMode (.getOptions configuration-base)))]
    (db/assoc-in project [:settings :mode] (keyword mode))))

(defn ^:private reset-editor-from-settings [project]
  (let [ui (db/get-in project [:run-configuration :ui])
        settings (db/get-in project [:settings])]
    (seesaw/text! (seesaw/select ui [:#nrepl-host]) (:nrepl-host settings))
    (seesaw/text! (seesaw/select ui [:#nrepl-port]) (:nrepl-port settings))
    (seesaw/text! (seesaw/select ui [:#project]) (:project settings))))

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
           (proxy+ [] com.intellij.openapi.options.SettingsEditor
             (createEditor [_]
               (let [editor-ui (build-editor-ui)]
                 (db/assoc-in project [:run-configuration :ui] editor-ui)
                 editor-ui))
             (applyEditorTo [_ configuration-base]
               (apply-editor-to configuration-base project))

             (resetEditorFrom [_ configuration-base]
               (setup-settings configuration-base project)
               (reset-editor-from-settings project))))

         (getState
           ([])
           ([executor ^ExecutionEnvironment env]
            (setup-settings this project)
            (proxy [CommandLineState] [env]
              (createConsole [_]
                (config.factory.base/build-console-view project "Connecting to nREPL server..."))
              (startProcess []
                (setup-process project))))))))))
