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

(defn ^:private setup-process [project]
  (logger/info "Connecting to nREPL process...")
  ;TODO: handle when file does not exist
  (when (read-port-file?)
    (let [base-path (.getBasePath ^Project project)
          repl-file (io/file base-path ".nrepl-port")
          port (slurp repl-file)]
      (swap! db/db* assoc-in [:settings :nrepl-port] (parse-long port))))

  (let [handler (NopProcessHandler.)]
    (swap! config.factory.base/current-repl* assoc :handler handler)
    (.addProcessListener handler
                         (proxy+ [] ProcessListener
                           (startNotified [_ _] (config.factory.base/repl-started project ""))
                           (processWillTerminate [_ _ _] (config.factory.base/repl-disconnected))))
    handler))

(defn manual? [editor]
  (.isSelected ^JRadioButton (seesaw/select editor [:#manual])))

(defn mode-id-key [repl-mode]
  (->> (seesaw/selection repl-mode)
       (seesaw/id-of)))

(defn ^:private build-editor-view []
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
(defn ^:private apply-editor-to [^RunConfigurationBase configuration-base]
  (let [editor @config.factory.base/editor-view*
        host (seesaw/text (seesaw/select editor [:#nrepl-host]))
        project (seesaw/text (seesaw/select editor [:#project]))
        mode (if (manual? editor) :manual-config :file-config)]
    (swap! db/db* assoc-in [:settings :nrepl-host] host)
    (.setNreplHost (.getOptions configuration-base) host)
    (swap! db/db* assoc-in [:settings :mode] mode)
    (.setMode (.getOptions configuration-base) (name mode))
    (when-let [nrepl-port (parse-long (seesaw/text (seesaw/select editor [:#nrepl-port])))]
      (swap! db/db* assoc-in [:settings :nrepl-port] nrepl-port)
      (.setNreplPort (.getOptions configuration-base) (str nrepl-port))
      (swap! db/db* assoc-in [:settings :project] host)
      (.setProject (.getOptions configuration-base) project))))

(defn ^:private setup-settings [^RunConfigurationBase configuration-base]
  (when-let [host (not-empty (.getNreplHost (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :nrepl-host] host))
  (when-let [nrepl-port (parse-long (.getNreplPort (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :nrepl-port] nrepl-port))
  (when-let [project (not-empty (.getProject (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :project] project))
  (when-let [mode (not-empty (.getMode (.getOptions configuration-base)))]
    (swap! db/db* assoc-in [:settings :mode] (keyword mode))))

(defn ^:private reset-editor-from-settings []
  (let [editor @config.factory.base/editor-view*
        settings (:settings @db/db*)]
    (seesaw/text! (seesaw/select editor [:#nrepl-host]) (:nrepl-host settings))
    (seesaw/text! (seesaw/select editor [:#nrepl-port]) (:nrepl-port settings))
    (seesaw/text! (seesaw/select editor [:#project]) (:project settings))))

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
            (setup-settings this)
            (proxy [CommandLineState] [env]
              (createConsole [_]
                (config.factory.base/build-console-view project "Connecting to nREPL server..."))
              (startProcess []
                (setup-process project))))))))))
