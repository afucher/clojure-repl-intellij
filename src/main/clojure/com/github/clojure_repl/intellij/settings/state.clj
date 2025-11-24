(ns com.github.clojure-repl.intellij.settings.state
  (:gen-class
   :name com.github.clojure_repl.intellij.settings.PluginSettingsState
   :state state
   :init init
   :implements [com.intellij.openapi.components.PersistentStateComponent])
  (:import
   [com.intellij.openapi.application ApplicationManager]))

(set! *warn-on-reflection* true)

(defn -init []
  [[] (atom {:auto-load-on-save false})])

(defn -getState [this]
  @(.state this))

(defn -loadState [this new-state]
  (when new-state
    (reset! (.state this) new-state)))

(defn get-instance []
  (.getService (ApplicationManager/getApplication)
               com.github.clojure_repl.intellij.settings.PluginSettingsState))

(defn auto-load-on-save-enabled? []
  (let [instance (get-instance)
        state-map (.getState instance)]
    (get state-map :auto-load-on-save false)))

(defn set-auto-load-on-save! [enabled?]
  (let [instance (get-instance)]
    (swap! (.state instance) assoc :auto-load-on-save enabled?)))


