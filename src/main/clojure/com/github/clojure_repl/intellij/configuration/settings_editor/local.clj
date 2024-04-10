(ns com.github.clojure-repl.intellij.configuration.settings-editor.local
  (:gen-class
   :state state
   :init init
   :name com.github.clojure_repl.intellij.configuration.settings_editor.Local
   :extends com.intellij.openapi.options.SettingsEditor)
  (:require
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.ui IdeBorderFactory]))

(set! *warn-on-reflection* false)

(defn ^:private build-editor-ui []
  (mig/mig-panel
   :border (IdeBorderFactory/createTitledBorder "nREPL connection")
   :items [[(seesaw/label "Project") ""]
           [(seesaw/combobox :id    :project
                             :model (->> (ProjectManager/getInstance)
                                         .getOpenProjects
                                         (map #(.getName ^Project %)))) "wrap"]]))

(defn -init []
  [[] (atom (build-editor-ui))])

(defn -createEditor [this]
  @(.state this))

(defn -applyEditorTo [this configuration]
  (let [ui @(.state this)
        project-path (seesaw/text (seesaw/select ui [:#project]))]
    (.setProject (.getOptions configuration) project-path)))

(defn -resetEditorFrom [this configuration]
  (let [ui @(.state this)
        options (.getOptions configuration)]
    (seesaw/text! (seesaw/select ui [:#project]) (.getProject options))))
