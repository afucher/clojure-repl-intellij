(ns com.github.clojure-repl.intellij.configuration.repl-client
  (:gen-class
   :init init
   :constructors {[] [String String String com.intellij.openapi.util.NotNullLazyValue]}
   :name com.github.clojure_repl.intellij.configuration.ReplClientRunConfigurationType
   :extends com.intellij.execution.configurations.SimpleConfigurationType)
  (:require
   [com.rpl.proxy-plus :refer [proxy+]]
   [nrepl.core :as nrepl]
   [seesaw.color :as color]
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
   [java.awt.event KeyEvent]
   [javax.swing JComponent JTextArea]
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

(defn with-background [^JComponent component color]
  (doto component
    (.setBackground (color/color color))))

(def ^:private repl-output-background "#1d252c")
(def ^:private repl-input-background "#666")

(defn ^:private build-console-view [{:keys [on-eval]}]
  (with-background
    (mig/mig-panel :items [[(with-background
                              (mig/mig-panel :id :repl-output-layout
                                             :items [[(with-background
                                                        (seesaw/text :id :repl-output-content
                                                                     :multi-line? true
                                                                     :editable? false
                                                                     :text "Clojure REPL")
                                                        repl-output-background) ""]])
                              repl-output-background)
                            "span, grow"]
                           [(seesaw/separator) "growx, gaptop 5, span"]
                           [(with-background
                              (mig/mig-panel :id :repl-input-layout
                                             :items [[(with-background
                                                        (seesaw/text :id :repl-input-content
                                                                     :multi-line? true
                                                                     :editable? true
                                                                     :listen [:key-pressed (fn [^KeyEvent key-event]
                                                                                             (when (= KeyEvent/VK_ENTER (.getKeyCode key-event))
                                                                                               (let [component (.getComponent key-event)
                                                                                                     current-code (seesaw/text component)]
                                                                                                 (seesaw/text! component "")
                                                                                                 (.consume key-event)
                                                                                                 (on-eval current-code))))]
                                                                     :text "")
                                                        repl-input-background) ""]])
                              repl-output-background)
                            "grow"]])
    repl-output-background))

(defn ^:private build-console []
  (let [console-view (build-console-view
                      {:on-eval (fn [code]
                                  (let [return (:value (first (send-repl-message {:op "eval" :code code :session (:session-id @state*)})))
                                        repl-output-content ^JTextArea (seesaw/select (:console @state*) [:#repl-output-content])]
                                    (.append repl-output-content
                                             (str "\n=> " return ""))))})]
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
