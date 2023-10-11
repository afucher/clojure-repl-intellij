(ns com.github.clojure-repl.intellij.configuration.repl-client
  (:gen-class
   :init init
   :constructors {[] [String String String com.intellij.openapi.util.NotNullLazyValue]}
   :name com.github.clojure_repl.intellij.configuration.ReplClientRunConfigurationType
   :extends com.intellij.execution.configurations.SimpleConfigurationType)
  (:require
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
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
   [com.intellij.openapi.util NotNullFactory NotNullLazyValue]
   [nrepl.transport FnTransport]))

(set! *warn-on-reflection* true)

(def ID "Clojure REPL")

(defonce state* (atom {:handler (NopProcessHandler.)
                       :console nil}))

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
  (with-open [conn ^FnTransport (nrepl/connect :port 8880)]
    (-> (nrepl/client conn 1000)
        (nrepl/message message)
        doall)))

(defn ^:private build-editor-view []
  (mig/mig-panel :items [[(seesaw/label "Nrepl port") ""]]))

(defn ^:private build-console []
  (let [console-view (ui.repl/build-console-view
                       ;; TODO set current nrepl host, port, clojure and java versions.
                      {:initial-text (str ";; Connected to nREPL server - nrepl://host:port\n"
                                          ";; Clojure x.xx.x, Java y.y.y")
                       :initial-ns "user"
                       :on-eval (fn [code]
                                  (first (send-repl-message {:op "eval" :code code :session (:session-id @state*)})))})]
    (swap! state* assoc :console console-view)
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
         (createConsole [_ _] (build-console))
         (startProcess [_]
           (swap! state* assoc :session-id (:new-session (first (send-repl-message {:op "clone"}))))
           (:handler @state*)))))))

(comment
  (def session-id (:new-session (first (send-repl-message {:op "clone"}))))
  (send-repl-message {:op "ls-sessions"})
  (send-repl-message {:op "load-file" :file (slurp "/home/greg/dev/nu/atlas-core/src/atlas_core/db/datomic/config.clj")})
  (send-repl-message {:op "decribe"})

  (let [return (:value (first (send-repl-message {:op "eval" :code "(+ 1 2)" :session session-id})))
        repl-output-content (seesaw/select (:console @state*) [:#repl-output-content])]
    (.append repl-output-content
             (str "\n=> " return ""))
    #_(.notifyTextAvailable (:handler @state*)
                            (str "=> " return "\n")
                            (Key. "output"))))
