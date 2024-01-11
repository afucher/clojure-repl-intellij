(ns com.github.clojure-repl.intellij.configuration.factory.base
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.execution.ui ConsoleView]
   [com.intellij.ide.plugins PluginManagerCore]
   [com.intellij.openapi.actionSystem AnAction]
   [com.intellij.openapi.extensions PluginId]))

(set! *warn-on-reflection* true)

(defonce current-repl* (atom {:handler nil
                              :console nil}))
(defonce editor-view* (atom nil))

(defn ^:private initial-repl-text []
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

(defn build-console-view [project loading-text]
  (reset! current-repl* {:handler nil
                         :console nil})
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

(defn repl-disconnected []
  (ui.repl/close-console (:console @current-repl*))
  (reset! current-repl* {:handler nil
                         :console nil})
  (swap! db/db* assoc :current-nrepl nil))

(defn repl-started [project extra-initial-text]
  (nrepl/clone-session)
  (nrepl/eval {:project project :code "*ns*"})
  (let [description (nrepl/describe)]
    (swap! db/db* assoc-in [:current-nrepl :ops] (:ops description))
    (swap! db/db* assoc-in [:current-nrepl :versions] (:versions description))
    (ui.repl/set-initial-text (:console @current-repl*) (str (initial-repl-text) extra-initial-text))))
