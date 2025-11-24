(ns com.github.clojure-repl.intellij.settings.configurable
  (:gen-class
   :name com.github.clojure_repl.intellij.settings.PluginConfigurable
   :state state
   :init init
   :implements [com.intellij.openapi.options.Configurable])
  (:require
   [com.github.clojure-repl.intellij.settings.state :as settings-state])
  (:import
   [javax.swing JCheckBox JPanel BoxLayout]))

(set! *warn-on-reflection* true)

(defn -init []
  [[] (atom {:auto-load-checkbox nil})])

(defn -getDisplayName [_]
  "Clojure REPL")

(defn -createComponent [this]
  (let [panel (JPanel.)
        auto-load-checkbox (JCheckBox. "Auto load file to REPL on save" (settings-state/auto-load-on-save-enabled?))]
    (.setLayout panel (BoxLayout. panel BoxLayout/Y_AXIS))
    (.add panel auto-load-checkbox)
    (swap! (.state this) assoc :auto-load-checkbox auto-load-checkbox)
    panel))

(defn -isModified [this]
  (let [checkbox ^JCheckBox (:auto-load-checkbox @(.state this))
        current-value (settings-state/auto-load-on-save-enabled?)]
    (not= (.isSelected checkbox) current-value)))

(defn -apply [this]
  (let [checkbox ^JCheckBox (:auto-load-checkbox @(.state this))]
    (settings-state/set-auto-load-on-save! (.isSelected checkbox))))

(defn -reset [this]
  (let [checkbox ^JCheckBox (:auto-load-checkbox @(.state this))
        current-value (settings-state/auto-load-on-save-enabled?)]
    (.setSelected checkbox current-value)))

(defn -disposeUIResources [this]
  (swap! (.state this) assoc :auto-load-checkbox nil))

