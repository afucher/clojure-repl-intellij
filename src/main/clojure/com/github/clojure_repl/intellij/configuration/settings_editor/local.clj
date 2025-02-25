(ns com.github.clojure-repl.intellij.configuration.settings-editor.local
  (:gen-class
   :state state
   :init init
   :name com.github.clojure_repl.intellij.configuration.settings_editor.Local
   :extends com.intellij.openapi.options.SettingsEditor)
  (:require
   [com.github.clojure-repl.intellij.project :as project]
   [com.github.clojure-repl.intellij.ui.components :as ui.components]
   [seesaw.core :as seesaw]
   [seesaw.layout]
   [seesaw.mig :as mig])
  (:import
   [com.github.clojure_repl.intellij.configuration ReplLocalRunOptions]
   [com.intellij.execution.configurations RunConfigurationBase]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.ui IdeBorderFactory]))

(set! *warn-on-reflection* true)

(defn ^:private build-editor-ui []
  (let [opened-projects (.getOpenProjects (ProjectManager/getInstance))
        project-type (name (or (project/project->project-type (first opened-projects)) :clojure))]
    (mig/mig-panel
     :border (IdeBorderFactory/createTitledBorder "nREPL connection")
     :items (->> [[(seesaw/label "Project") ""]
                  [(seesaw/combobox :id :project
                                    :model (mapv #(.getName ^Project %) opened-projects)) "wrap"]
                  [(seesaw/label "Type") ""]
                  [(doto
                    (seesaw/combobox :id :project-type
                                     :model (map name project/types))
                     (seesaw/selection! project-type)) "wrap"]
                  (ui.components/multi-field
                   :label (if (= "lein" project-type) "Profiles" "Aliases")
                   :group-id :aliases-group
                   :button-id :add-alias
                   :prefix-id "alias-"
                   :columns 10
                   :initial-text "")
                  (ui.components/multi-field
                   :label "Env vars"
                   :group-id :env-vars-group
                   :button-id :add-env-var
                   :prefix-id "env-var-"
                   :columns 15
                   :initial-text "EXAMPLE_VAR=foo")
                  (ui.components/multi-field
                   :label "JVM args"
                   :group-id :jvm-args-group
                   :button-id :add-jvm-arg
                   :prefix-id "jvm-arg-"
                   :columns 15
                   :initial-text "-Dexample=foo")]
                 flatten
                 (remove nil?)
                 (partition 2)
                 vec))))

(defn -init []
  [[] (atom (build-editor-ui))])

(defn ^:private update-configuration-name [^RunConfigurationBase configuration]
  (when (contains? #{"Unnamed" ""} (.getName configuration))
    (.setName configuration "Local REPL")))

(set! *warn-on-reflection* false)

(defn -createEditor [this]
  @(.state this))

(defn -applyEditorTo [this configuration]
  (update-configuration-name configuration)
  (let [ui @(.state this)
        options ^ReplLocalRunOptions (.getOptions configuration)
        project-path (seesaw/text (seesaw/select ui [:#project]))
        type (seesaw/text (seesaw/select ui [:#project-type]))
        aliases (ui.components/field-values-from-multi-field ui :aliases-group)
        env-vars (ui.components/field-values-from-multi-field ui :env-vars-group)
        jvm-args (ui.components/field-values-from-multi-field ui :jvm-args-group)]
    (.setProject options project-path)
    (.setProjectType options type)
    (.setAliases options aliases)
    (.setEnvVars options env-vars)
    (.setJvmArgs options jvm-args)))

(defn -resetEditorFrom [this configuration]
  (update-configuration-name configuration)
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
        aliases (.getAliases options)
        env-vars (.getEnvVars options)
        jvm-args (.getJvmArgs options)]
    (seesaw/selection! (seesaw/select ui [:#project-type]) type)
    (seesaw/text! (seesaw/select ui [:#project]) project-name)
    (doseq [alias aliases]
      (ui.components/add-field-to-multi-field! ui :aliases-group alias))
    (doseq [env-var env-vars]
      (ui.components/add-field-to-multi-field! ui :env-vars-group env-var))
    (doseq [jvm-arg jvm-args]
      (ui.components/add-field-to-multi-field! ui :jvm-args-group jvm-arg))))
