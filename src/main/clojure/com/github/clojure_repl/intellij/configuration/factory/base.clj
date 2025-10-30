(ns com.github.clojure-repl.intellij.configuration.factory.base
  (:require
   [com.github.clojure-repl.intellij.action.custom-code-actions :as custom-code-actions]
   [com.github.clojure-repl.intellij.config :as config]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.execution.ui ConsoleView]
   [com.intellij.ide ActivityTracker]
   [com.intellij.ide.plugins PluginManagerCore]
   [com.intellij.openapi.actionSystem ActionManager AnAction]
   [com.intellij.openapi.extensions PluginId]
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* true)

(defn ^:private initial-repl-text [project]
  (let [{:keys [clojure java nrepl]} (db/get-in project [:current-nrepl :versions])]
    (str (format ";; Connected to nREPL server - nrepl://%s:%s\n"
                 (db/get-in project [:current-nrepl :nrepl-host])
                 (db/get-in project [:current-nrepl :nrepl-port]))
         (format ";; Clojure REPL Intellij %s\n"
                 (.getVersion
                  (PluginManagerCore/getPlugin (PluginId/getId "com.github.clojure-repl"))))
         (format ";; Clojure %s, Java %s, nREPL %s"
                 (:version-string clojure)
                 (:version-string java)
                 (:version-string nrepl)))))

(defn ^:private build-console-actions
  []
  (let [manager (ActionManager/getInstance)
        clear-repl (.getAction manager "ClojureREPL.ClearReplOutput")
        history-up (.getAction manager "ClojureREPL.HistoryUp")
        history-down (.getAction manager "ClojureREPL.HistoryDown")
        refresh-namespaces (.getAction manager "ClojureREPL.RefreshAll")
        interrupt (.getAction manager "ClojureREPL.Interrupt")]
    [clear-repl history-up history-down refresh-namespaces interrupt]))

(defn build-console-view [project loading-text]
  (db/assoc-in! project [:console :process-handler] nil)
  (db/assoc-in! project [:console :ui] (ui.repl/build-console
                                        project
                                        {:on-eval (fn [code]
                                                    (nrepl/eval-from-repl
                                                     {:project project
                                                      :code code
                                                      :ns (db/get-in project [:current-nrepl :ns])}))}))
  (ui.repl/append-output project loading-text)
  (proxy+ [] ConsoleView
          (getComponent [_] (db/get-in project [:console :ui]))
          (getPreferredFocusableComponent [_] (db/get-in project [:console :ui]))
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
          (createConsoleActions [_] (into-array AnAction (build-console-actions)))
          (allowHeavyFilters [_])))

(defn ^:private on-repl-evaluated [project {:keys [out err]}]
  (when err
    (ui.repl/append-output project (str "\n" err)))
  (when out
    (ui.repl/append-output project (str "\n" out))))

(defn ^:private on-ns-changed [project _]
  (ui.repl/clear-input project))

(defn ^:private trigger-ui-update
  "IntelliJ actions status (visibility/enable) depend on IntelliJ calls an update of the UI
   but the call of update is not guaranteed. This function triggers the update of the UI.
   @see https://github.com/JetBrains/intellij-community/blob/08d00166f92aaf0eedfa6fc9c147ef10ea86da27/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/AnAction.java#L361"
  [& _]
  (.inc (ActivityTracker/getInstance)))

(defn ^:private default-async-msg-handler [project {:keys [out err status]}]
  (when (contains? (set status) "done")
    (trigger-ui-update))
  (when err
    (ui.repl/append-output project (str "\n" err)))
  (when out
    (ui.repl/append-output project (str "\n" out))))

(defn repl-disconnected [^Project project]
  (ui.repl/close-console project (db/get-in project [:console :ui]))
  (db/assoc-in! project [:console :process-handler] nil)
  (db/assoc-in! project [:console :ui] nil)
  (db/assoc-in! project [:current-nrepl] nil)
  (db/assoc-in! project [:classpath] [])
  (db/update-in! project [:on-repl-evaluated-fns] (fn [fns] (remove #(contains? #{on-repl-evaluated trigger-ui-update} %) fns))))

(defn repl-started [project extra-initial-text]
  (nrepl/start-client!
   :project project
   :on-receive-async-message (fn [msg] (default-async-msg-handler project msg)))
  (nrepl/clone-session project)
  (let [description (nrepl/describe project)
        classpath (nrepl/classpath project)]
    (when (:out-subscribe (:ops description))
      (nrepl/out-subscribe project))
    (db/assoc-in! project [:current-nrepl :ops] (:ops description))
    (db/assoc-in! project [:current-nrepl :versions] (:versions description))
    (db/assoc-in! project [:current-nrepl :entry-history] '())
    (db/assoc-in! project [:current-nrepl :entry-index] -1)
    (db/assoc-in! project [:current-nrepl :ns] "user")
    (db/assoc-in! project [:file->ns] {})
    (db/assoc-in! project [:classpath] (:classpath classpath))

    (future
      (try
        (custom-code-actions/register-custom-code-actions (config/from-project project) project)
        (catch Throwable e
          (logger/error "Error registering custom code actions:" e))))

    (ui.repl/set-repl-started-initial-text project
                                           (db/get-in project [:console :ui])
                                           (str (initial-repl-text project) extra-initial-text))
    (db/update-in! project [:on-ns-changed-fns] #(conj % on-ns-changed trigger-ui-update))))
