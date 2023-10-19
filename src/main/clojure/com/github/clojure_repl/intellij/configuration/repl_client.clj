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
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.util NotNullFactory NotNullLazyValue]
   [com.intellij.ui IdeBorderFactory]))

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

(defn ^:private build-editor-view []
  (mig/mig-panel
   :border (IdeBorderFactory/createTitledBorder "NREPL connection")
   :items [[(seesaw/label "Host") ""]
           [(seesaw/text :id :nrepl-host
                         :columns 20) "wrap"]
           [(seesaw/label "Port") ""]
           [(seesaw/text :id :nrepl-port
                         :columns 8) "wrap"]]))

(defn ^:private initial-repl-text []
  (let [{:keys [clojure java nrepl]} (:versions (nrepl/describe))]
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

(defn ^:private build-console-view []
  (reset! current-repl* initial-current-repl)
  (swap! current-repl* assoc :console (ui.repl/build-console
                                       {:initial-text (initial-repl-text)
                                        :on-eval (fn [code]
                                                   (nrepl/eval :code code))}))
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

(defn ^:private start-process []
  (nrepl/clone-session)
  (nrepl/eval "*ns*")
  (let [handler (NopProcessHandler.)]
    (swap! current-repl* assoc :handler handler)
    (.addProcessListener handler
                         (proxy+ [] ProcessListener
                           (processWillTerminate [_ _ _]
                             (ui.repl/close-console (:console @current-repl*)))))
    handler))

(defn ^:private apply-editor-to [^RunConfigurationBase configuration-base]
  (let [editor @editor*
        host (seesaw/text (seesaw/select editor [:#nrepl-host]))]
    (swap! db/db* assoc-in [:settings :nrepl-host] host)
    (.setNreplHost (.getOptions configuration-base) host)
    (when-let [nrepl-port (parse-long (seesaw/text (seesaw/select editor [:#nrepl-port])))]
      (swap! db/db* assoc-in [:settings :nrepl-port] nrepl-port)
      (.setNreplPort (.getOptions configuration-base) (str nrepl-port)))))

(defn ^:private reset-editor-from [^RunConfigurationBase configuration-base]
  (let [editor @editor*
        host (or (not-empty (.getNreplHost (.getOptions configuration-base)))
                 (-> @db/db* :settings :nrepl-host))]
    (swap! db/db* assoc-in [:settings :nrepl-host] host)
    (seesaw/text! (seesaw/select editor [:#nrepl-host]) host)
    (when-let [nrepl-port (parse-long (.getNreplPort (.getOptions configuration-base)))]
      (swap! db/db* assoc-in [:settings :nrepl-port] nrepl-port)
      (seesaw/text! (seesaw/select editor [:#nrepl-port]) nrepl-port))))

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

         (resetEditorFrom [_ configuraiton-base]
           (reset-editor-from configuraiton-base))))

     (getState [executor ^ExecutionEnvironment env]
       (proxy [CommandLineState] [env]
         (createConsole [_] (build-console-view))
         (startProcess [] (start-process)))))))
