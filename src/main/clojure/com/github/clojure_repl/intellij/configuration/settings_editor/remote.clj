(ns com.github.clojure-repl.intellij.configuration.settings-editor.remote
  (:gen-class
   :state state
   :init init
   :name com.github.clojure_repl.intellij.configuration.settings_editor.Remote
   :extends com.intellij.openapi.options.SettingsEditor)
  (:require
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.intellij.execution.configurations RunConfigurationBase]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.ui IdeBorderFactory]
   [javax.swing JRadioButton]))

(set! *warn-on-reflection* false)

(defn ^:private mode-id-key [repl-mode]
  (->> (seesaw/selection repl-mode)
       (seesaw/id-of)))

(defn ^:private build-editor-ui []
  (let [repl-mode-group (seesaw/button-group)
        panel (mig/mig-panel
               :border (IdeBorderFactory/createTitledBorder "NREPL connection")
               :items [[(seesaw/radio :text "Manual"
                                      :id :manual
                                      :group repl-mode-group
                                      :mnemonic \M
                                      :selected? true) "wrap"]
                       [(seesaw/label "Host") ""]
                       [(seesaw/text :id :nrepl-host
                                     :columns 20) "wrap"]
                       [(seesaw/label "Port") ""]
                       [(seesaw/text :id :nrepl-port
                                     :columns 8) "wrap"]
                       [(seesaw/radio :text "Read from repl file"
                                      :id :repl-file
                                      :group repl-mode-group
                                      :mnemonic \R) "wrap"]
                       [(seesaw/label "Project") ""]
                       [(seesaw/combobox :id :project
                                         :model (->> (ProjectManager/getInstance)
                                                     .getOpenProjects
                                                     (map #(.getName ^Project %)))) "wrap"]])]
    (seesaw/listen repl-mode-group :action
                   (fn [_e]
                     (let [mode-key (mode-id-key repl-mode-group)
                           manual? (= mode-key :manual)]
                       (seesaw/config! (seesaw/select panel [:#nrepl-host]) :enabled? manual?)
                       (seesaw/config! (seesaw/select panel [:#nrepl-port]) :enabled? manual?))))

    panel))

(defn -init []
  [[] (atom (build-editor-ui))])

(defn -createEditor [this]
  @(.state this))

(defn ^:private manual? [editor]
  (.isSelected ^JRadioButton (seesaw/select editor [:#manual])))

(defn ^:private update-configuration-name [^RunConfigurationBase configuration]
  (when (contains? #{"Unnamed" ""} (.getName configuration))
    (.setName configuration "Remote REPL")))

(defn -applyEditorTo [this configuration]
  (update-configuration-name configuration)
  (let [ui @(.state this)
        host (seesaw/text (seesaw/select ui [:#nrepl-host]))
        project-path (seesaw/text (seesaw/select ui [:#project]))
        mode (if (manual? ui) :manual-config :file-config)]
    (.setNreplHost (.getOptions configuration) host)
    (.setMode (.getOptions configuration) (name mode))
    (when-let [nrepl-port (parse-long (seesaw/text (seesaw/select ui [:#nrepl-port])))]
      (.setNreplPort (.getOptions configuration) (str nrepl-port))
      (.setProject (.getOptions configuration) project-path))))

(defn -resetEditorFrom [this configuration]
  (update-configuration-name configuration)
  (let [ui @(.state this)
        options (.getOptions configuration)
        mode (keyword (.getMode options))]
    (if (= :manual-config mode)
      (do
        (seesaw/config! (seesaw/select ui [:#manual]) :selected? true)
        (seesaw/config! (seesaw/select ui [:#repl-file]) :selected? false)
        (seesaw/config! (seesaw/select ui [:#nrepl-host]) :enabled? true)
        (seesaw/config! (seesaw/select ui [:#nrepl-port]) :enabled? true))
      (do
        (seesaw/config! (seesaw/select ui [:#manual]) :selected? false)
        (seesaw/config! (seesaw/select ui [:#repl-file]) :selected? true)
        (seesaw/config! (seesaw/select ui [:#nrepl-host]) :enabled? false)
        (seesaw/config! (seesaw/select ui [:#nrepl-port]) :enabled? false)))
    (seesaw/text! (seesaw/select ui [:#nrepl-host]) (.getNreplHost options))
    (seesaw/text! (seesaw/select ui [:#nrepl-port]) (.getNreplPort options))
    (seesaw/text! (seesaw/select ui [:#project]) (.getProject options))))
