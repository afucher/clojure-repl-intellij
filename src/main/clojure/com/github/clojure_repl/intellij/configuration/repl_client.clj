(ns com.github.clojure-repl.intellij.configuration.repl-client
  (:gen-class
   :init init
   :constructors {[] [String String String com.intellij.openapi.util.NotNullLazyValue]}
   :name com.github.clojure_repl.intellij.configuration.ReplClientRunConfigurationType
   :extends com.intellij.execution.configurations.SimpleConfigurationType)
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.rpl.proxy-plus :refer [proxy+]]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplClientRunOptions]
   [com.intellij.execution.configurations CommandLineState RunConfigurationBase]
   [com.intellij.execution.process NopProcessHandler ProcessListener]
   [com.intellij.execution.runners ExecutionEnvironment]
   [com.intellij.execution.ui ConsoleView]
   [com.intellij.icons AllIcons$Nodes]
   [com.intellij.ide.plugins PluginManagerCore]
   [com.intellij.openapi.actionSystem AnAction]
   [com.intellij.openapi.extensions PluginId]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.openapi.util NotNullFactory NotNullLazyValue]
   [com.intellij.ui IdeBorderFactory]
   [javax.swing JRadioButton JTextField]))

(set! *warn-on-reflection* false)

(def ID "Clojure REPL")

(def initial-current-repl {:handler nil
                           :console nil})
(defonce current-repl* (atom initial-current-repl))
(defonce editor* (atom nil))

(defn -init []
  [[ID
    "Clojure REPL"
    "Connect to existing REPL"
    (NotNullLazyValue/createValue
     (reify NotNullFactory
       (create [_]
         AllIcons$Nodes/Console)))] nil])

(defn -getId [_] ID)
(defn -getHelpTopic [_] "Clojure REPL")
(defn -getOptionsClass [_] ReplClientRunOptions)

(defn custom-renderer [^Project item]
  (seesaw.core/text (.getName item)))

(defn is-manual? [editor]
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
                                                      (map #(.getName %)))) "wrap"]])]
    (seesaw/listen repl-mode-group :action
                   (fn [e]
                     (let [mode-key (mode-id-key repl-mode-group)
                           manual? (= mode-key :manual)]
                       (.setEnabled ^JTextField (seesaw/select panel [:#nrepl-host]) manual?)
                       (.setEnabled ^JTextField (seesaw/select panel [:#nrepl-port]) manual?))))

    panel))

(defn ^:private echo-file-loaded [file]
  (ui.repl/append-text (:console @current-repl*) (str "\nLoaded file " file "\n")))

(defn ^:private echo-evaluated [{:keys [value]}]
  (ui.repl/append-text (:console @current-repl*) (str "\n" value "\n")))

(defn ^:private register-listeners []
  (swap! db/db* update :on-repl-file-loaded-fns conj {:project (-> @db/db* :settings :project) :fn #'echo-file-loaded})
  (swap! db/db* update :on-repl-evaluated-fns conj {:project (-> @db/db* :settings :project) :fn #'echo-evaluated}))

(defn ^:private unregister-listeners []
  (let [db @db/db*
        project (-> db :settings :project)]
    (swap! db/db* assoc :on-repl-file-loaded-fns
           (remove #(= (:project %) project) (:on-repl-file-loaded-fns db)))
    (swap! db/db* assoc :on-repl-evaluated-fns
           (remove #(= (:project %) project) (:on-repl-evaluated-fns db)))))

(defn ^:private initial-repl-text []
  (let [{:keys [clojure java nrepl]} (-> @db/db* :versions)]
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

(defn ^:private build-console-view [project]
  (reset! current-repl* initial-current-repl)
  (swap! current-repl* assoc :console (ui.repl/build-console
                                       {:initial-text (initial-repl-text)
                                        :on-eval (fn [code]
                                                   (nrepl/eval {:project project :code code}))}))
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

(defn ^:private should-read-port-file? []
  (= (-> @db/db* :settings :mode) "repl-file"))

(defn ^:private start-process [project]
  (logger/info "Starting NREPL process...")
  (logger/info (str "project: " project))
  (logger/info (str "read port from file? : " (should-read-port-file?)))
  ;TODO: handle when file does not exist
  (when (should-read-port-file?)
      (if-let [project (->> (ProjectManager/getInstance)
                            .getOpenProjects
                            (filter #(= (.getName %) (.getName project)))
                            first)]
        (let [base-path (.getBasePath ^Project project)
              repl-file (str base-path "/.nrepl-port")
              port (slurp repl-file)]
          (swap! db/db* assoc-in [:settings :nrepl-port] (parse-long port)))))

  (nrepl/clone-session)
  (nrepl/eval {:project project :code "*ns*"})
  (let [description (nrepl/describe)]
    (swap! db/db* assoc :ops (:ops description))
    (swap! db/db* assoc :versions (:versions description)))
  (let [handler (NopProcessHandler.)]
    (swap! current-repl* assoc :handler handler)
    (register-listeners)
    (.addProcessListener handler
                         (proxy+ [] ProcessListener
                           (processWillTerminate [_ _ _]
                             (unregister-listeners)
                             (ui.repl/close-console (:console @current-repl*)))))
    handler))

(defn ^:private apply-editor-to [^RunConfigurationBase configuration-base]
  (let [editor @editor*
        host (seesaw/text (seesaw/select editor [:#nrepl-host]))
        project (seesaw/text (seesaw/select editor [:#project]))
        mode (if (is-manual? editor) "manual" "repl-file")]
    (swap! db/db* assoc-in [:settings :nrepl-host] host)
    (.setNreplHost (.getOptions configuration-base) host)
    (swap! db/db* assoc-in [:settings :mode] mode)
    (.setMode (.getOptions configuration-base) mode)
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
    (swap! db/db* assoc-in [:settings :mode] mode)))

(defn ^:private reset-editor-from-settings []
  (let [editor @editor*
        settings (:settings @db/db*)]
    (seesaw/text! (seesaw/select editor [:#nrepl-host]) (:nrepl-host settings))
    (seesaw/text! (seesaw/select editor [:#nrepl-port]) (:nrepl-port settings))
    (seesaw/text! (seesaw/select editor [:#project]) (:project settings))))

(defn -createTemplateConfiguration
  ([this project _]
   (-createTemplateConfiguration this project))
  ([factory-this ^Project project]
   (proxy [RunConfigurationBase] [project factory-this "Connect to existing REPL"]
     (getConfigurationEditor []
       (proxy+ [] com.intellij.openapi.options.SettingsEditor
         (createEditor [_]
           (let [editor-view (build-editor-view)]
             (reset! editor* editor-view)
             editor-view))
         (applyEditorTo [_ configuration-base]
           (apply-editor-to configuration-base))

         (resetEditorFrom [_ configuration-base]
           (setup-settings configuration-base)
           (reset-editor-from-settings))))

     (getState [executor ^ExecutionEnvironment env]
       (setup-settings this)
       (proxy [CommandLineState] [env]
         (createConsole [_]
           (build-console-view project))
         (startProcess []
           (start-process project)))))))
