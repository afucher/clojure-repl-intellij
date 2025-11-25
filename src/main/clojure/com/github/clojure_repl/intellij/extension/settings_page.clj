(ns com.github.clojure-repl.intellij.extension.settings-page
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.SettingsPage
   :implements [com.intellij.openapi.options.Configurable]
   :state state
   :init init)
  (:require
   [seesaw.core :as seesaw])
  (:import
   [com.github.clojure_repl.intellij ClojureReplSettings]
   [com.intellij.ui IdeBorderFactory]))

(set! *warn-on-reflection* true)

(defn -init []
  [[] (atom nil)])

(defn -getDisplayName [_this]
  "Clojure REPL")

(defn ^:private create-panel []
  (let [settings (ClojureReplSettings/getInstance)
        load-on-save-checkbox (seesaw/checkbox
                               :id :load-on-save
                               :text "Load file to REPL on save"
                               :selected? (.getLoadFileOnSave settings))
        description-label (seesaw/label
                           :text "<html>When enabled, automatically loads Clojure files (.clj, .cljc, .cljs)<br>to the connected REPL after saving.</html>"
                           :foreground "#888888")]
    (seesaw/vertical-panel
     :border (IdeBorderFactory/createTitledBorder "Auto-evaluation")
     :items [load-on-save-checkbox
             description-label])))

(set! *warn-on-reflection* false)

(defn -createComponent [this]
  (let [panel (create-panel)]
    (reset! (.state this) panel)
    panel))

(defn -isModified [this]
  (when-let [panel @(.state this)]
    (let [settings (ClojureReplSettings/getInstance)
          checkbox (seesaw/select panel [:#load-on-save])]
      (not= (seesaw/selection checkbox) (.getLoadFileOnSave settings)))))

(defn -apply [this]
  (when-let [panel @(.state this)]
    (let [settings (ClojureReplSettings/getInstance)
          checkbox (seesaw/select panel [:#load-on-save])]
      (.setLoadFileOnSave settings (boolean (seesaw/selection checkbox))))))

(defn -reset [this]
  (when-let [panel @(.state this)]
    (let [settings (ClojureReplSettings/getInstance)
          checkbox (seesaw/select panel [:#load-on-save])]
      (seesaw/selection! checkbox (.getLoadFileOnSave settings)))))

(defn -disposeUIResources [this]
  (reset! (.state this) nil))