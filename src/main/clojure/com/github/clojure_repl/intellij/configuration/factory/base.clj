(ns com.github.clojure-repl.intellij.configuration.factory.base
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.execution.ui ConsoleView]
   [com.intellij.ide.plugins PluginManagerCore]
   [com.intellij.openapi.actionSystem ActionManager AnAction]
   [com.intellij.openapi.extensions PluginId]
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.project Project]
   [com.github.clojure_repl.intellij Icons]
   [com.intellij.openapi.actionSystem CommonDataKeys]))

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
        up (proxy+
            ["Entry history navigation up" "Entry history navigation up" Icons/CLOJURE_REPL]
            AnAction
             (actionPerformed [_this event]
                              (let [editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)
                                    project ^Project (or (.getData event CommonDataKeys/PROJECT)
                                                         (.getProject editor))]
                                   (ui.repl/history-up project))))
        down (proxy+
              ["Entry history navigation down" "Entry history navigation down" Icons/CLOJURE_REPL]
              AnAction
               (actionPerformed [_this _event] #_(ui.repl/clear-repl project (db/get-in project [:console :ui]))))]
    [clear-repl up down]))

(defn build-console-view [project loading-text]
  (db/assoc-in! project [:console :process-handler] nil)
  (db/assoc-in! project [:console :ui] (ui.repl/build-console
                                        project
                                        {:on-eval (fn [code]
                                                    (nrepl/eval {:project project :code code}))}))
  (ui.repl/append-text (db/get-in project [:console :ui]) loading-text)
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
    (ui.repl/append-result-text project (db/get-in project [:console :ui]) err))
  (when out
    (ui.repl/append-result-text project (db/get-in project [:console :ui]) out)))

(defn repl-disconnected [^Project project]
  (ui.repl/close-console project (db/get-in project [:console :ui]))
  (db/assoc-in! project [:console :process-handler] nil)
  (db/assoc-in! project [:console :ui] nil)
  (db/assoc-in! project [:current-nrepl] nil)
  (db/update-in! project [:on-repl-evaluated-fns] (fn [fns] (remove #(= on-repl-evaluated %) fns))))

(defn repl-started [project extra-initial-text]
  (nrepl/start-client!
   :project project
   :on-receive-async-message (fn [msg]
                               (when (:out msg)
                                 (ui.repl/append-result-text project (db/get-in project [:console :ui]) (:out msg)))))
  (nrepl/clone-session project)
  (nrepl/eval {:project project :code "*ns*"})
  (let [description (nrepl/describe project)]
    (when (:out-subscribe (:ops description))
      (nrepl/out-subscribe project))
    (db/assoc-in! project [:current-nrepl :ops] (:ops description))
    (db/assoc-in! project [:current-nrepl :versions] (:versions description))
    (db/assoc-in! project [:current-nrepl :entry-history] [])
    (db/assoc-in! project [:current-nrepl :entry-index] -1)
    (ui.repl/set-initial-text project (db/get-in project [:console :ui]) (str (initial-repl-text project) extra-initial-text))
    (db/update-in! project [:on-repl-evaluated-fns] #(conj % on-repl-evaluated))))
