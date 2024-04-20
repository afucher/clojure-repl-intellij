(ns com.github.clojure-repl.intellij.configuration.settings-editor.local
  (:gen-class
   :state state
   :init init
   :name com.github.clojure_repl.intellij.configuration.settings_editor.Local
   :extends com.intellij.openapi.options.SettingsEditor)
  (:require
   [com.github.clojure-repl.intellij.project :as project]
   [seesaw.core :as seesaw]
   [seesaw.layout]
   [seesaw.mig :as mig])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplLocalRunOptions]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.ui IdeBorderFactory]
   [java.awt.event ActionEvent]
   [javax.swing JComponent]))

(set! *warn-on-reflection* true)

(defn ^:private alias-ui [alias-id alias-text]
  (let [text-id (keyword (str "alias-" alias-id))]
    (seesaw/text :id text-id
                 :text alias-text
                 :columns 10)))

(defn ^:private add-alias-field [ui alias]
  (let [aliases-group ^JComponent (seesaw/select ui [:#aliases-group])
        new-id (.getComponentCount aliases-group)]
    (seesaw.layout/add-widget aliases-group (alias-ui new-id alias))
    (seesaw.layout/handle-structure-change aliases-group)))

(defn ^:private build-editor-ui []
  (let [opened-projects (.getOpenProjects (ProjectManager/getInstance))
        project-type (name (project/project->project-type (first opened-projects)))]
    (mig/mig-panel
     :border (IdeBorderFactory/createTitledBorder "nREPL connection")
     :items (remove
             nil?
             [[(seesaw/label "Project") ""]
              [(seesaw/combobox :id    :project
                                :model (mapv #(.getName ^Project %) opened-projects)) "wrap"]
              [(seesaw/label "Type") ""]
              [(doto
                (seesaw/combobox :id    :project-type
                                 :model (map name project/types))
                 (seesaw/selection! project-type)) "wrap"]
              [(seesaw/label (if (= "lein" project-type) "Profiles" "Aliases")) ""]
              [(mig/mig-panel
                :constraints ["gap 0"]
                :id :aliases-group
                :items []) "gap 0"]
              [(seesaw/button :id :add-alias
                              :size [36 :by 36]
                              :text "+"
                              :listen [:action (fn [^ActionEvent event]
                                                 (let [button (.getSource event)
                                                       parent (.getParent ^JComponent button)]
                                                   (add-alias-field parent "")))]) "gap 0, wrap"]]))))

(defn -init []
  [[] (atom (build-editor-ui))])

(set! *warn-on-reflection* false)

(defn -createEditor [this]
  @(.state this))

(defn -applyEditorTo [this configuration]
  (let [ui @(.state this)
        options ^ReplLocalRunOptions (.getOptions configuration)
        project-path (seesaw/text (seesaw/select ui [:#project]))
        aliases (filterv not-empty (mapv seesaw/text (.getComponents (seesaw/select ui [:#aliases-group]))))
        type (seesaw/text (seesaw/select ui [:#project-type]))]
    (.setProject options project-path)
    (.setAliases options aliases)
    (.setProjectType options type)))

(defn -resetEditorFrom [this configuration]
  (let [ui @(.state this)
        options ^ReplLocalRunOptions (.getOptions configuration)
        project-name (or (not-empty (.getProject options))
                         (-> (ProjectManager/getInstance) .getOpenProjects first .getName))
        project (->> (ProjectManager/getInstance)
                     .getOpenProjects
                     (filter #(= project-name (.getName ^Project %)))
                     first)
        type (or (some-> options .getProjectType not-empty)
                 (name (project/project->project-type project)))
        aliases (.getAliases options)]
    (seesaw/selection! (seesaw/select ui [:#project-type]) type)
    (doseq [alias aliases]
      (add-alias-field ui alias))
    (seesaw/text! (seesaw/select ui [:#project]) project-name)))
