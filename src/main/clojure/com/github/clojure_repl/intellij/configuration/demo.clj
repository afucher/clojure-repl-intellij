(ns com.github.clojure-repl.intellij.configuration.demo
  (:gen-class
   :init init
   :constructors {[] [String String String com.intellij.openapi.util.NotNullLazyValue]}
   :name com.github.clojure_repl.intellij.configuration.DemoRunConfigurationType
   :extends com.intellij.execution.configurations.SimpleConfigurationType)
  (:require
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.rpl.proxy-plus :refer [proxy+]]
   [nrepl.core :as nrepl]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.intellij.execution.configurations CommandLineState RunConfigurationBase]
   [com.intellij.execution.process NopProcessHandler]
   [com.intellij.execution.runners ExecutionEnvironment]
   [com.intellij.execution.ui ConsoleView]
   [com.intellij.icons AllIcons$Nodes]
   [com.intellij.openapi.actionSystem AnAction]
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.util Key NotNullFactory NotNullLazyValue]
   [javax.swing JPanel]
   [nrepl.transport FnTransport]))

(set! *warn-on-reflection* true)

(def ID "Clojure REPL")

(defonce state* (atom {:handler (NopProcessHandler.)}))

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

(defn ^:private send-repl-message [message]
  (with-open [conn ^FnTransport (nrepl/connect :port 45057)]
    (-> (nrepl/client conn 1000)
        (nrepl/message message)
        doall)))

(defn ^:private build-editor-view []
  (mig/mig-panel :items [[(seesaw/label "Nrepl port") ""]]))

(defn ^:private build-console-view []
  (mig/mig-panel :items [[(seesaw/label "Bar") ""]]))

(defn -createTemplateConfiguration
  ([this ^Project project _]
   (-createTemplateConfiguration this project))
  ([this ^Project project]
   (proxy+ [project this "Connect to existing REPL"] RunConfigurationBase
     (getConfigurationEditor [_]
       (proxy+ [] com.intellij.openapi.options.SettingsEditor
         (resetEditorFrom [_ _])
         (applyEditorTo [_ _])
         (createEditor [_] (build-editor-view))))

     (getState [_ executor ^ExecutionEnvironment env]
       (proxy+ [env] CommandLineState
         (createConsole [_ _]
           (let [console-view (build-console-view)]
             (proxy+ [] ConsoleView
               (getComponent [_] console-view)
               (getPreferredFocusableComponent [_] console-view)
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
               (allowHeavyFilters [_]))))
         (startProcess [_]
           #_(with-open [conn ^FnTransport (nrepl/connect :port 45057)]
               (swap! state* assoc :client (nrepl/client conn 1000)))
           (:handler @state*)))))))

(comment
  (def session-id (:new-session (first (send-repl-message {:op "clone"}))))
  (send-repl-message {:op "ls-sessions"})
  (send-repl-message {:op "load-file" :file (slurp "/home/greg/dev/nu/atlas-core/src/atlas_core/db/datomic/config.clj")})
  (send-repl-message {:op "decribe"})

  (let [return (:value (first (send-repl-message {:op "eval" :code "(+ 1 2)" :session session-id})))]
    (.notifyTextAvailable (:handler @state*)
                          (str "=> " return "\n")
                          (Key. "output"))))
