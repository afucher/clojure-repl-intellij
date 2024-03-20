(ns com.github.clojure-repl.intellij.configuration.editor
  (:gen-class
   :name com.github.clojure_repl.intellij.configuration.SettingsEditor
   :extends com.intellij.openapi.options.SettingsEditor)
  (:require
   [com.github.clojure-repl.intellij.configuration.factory.base :as config.factory.base]
    [com.github.ericdallo.clj4intellij.logger :as logger]
    [seesaw.core :as seesaw]
    [seesaw.mig :as mig])
  (:import
    [com.intellij.execution.configurations RunConfigurationBase]
    [com.intellij.openapi.project Project ProjectManager]
    [com.intellij.ui IdeBorderFactory]
    [javax.swing JRadioButton JTextField]))

  (defn manual? [editor]
    (.isSelected ^JRadioButton (seesaw/select editor [:#manual])))

  (defn mode-id-key [repl-mode]
    (->> (seesaw/selection repl-mode)
         (seesaw/id-of)))
(set! *warn-on-reflection* false)
(defn ^:private apply-editor-to [^RunConfigurationBase configuration-base]
  (logger/info "apply-editor-to")
  (let [editor @config.factory.base/editor-view*
        host (seesaw/text (seesaw/select editor [:#nrepl-host]))
        project (seesaw/text (seesaw/select editor [:#project]))
        mode (if (manual? editor) :manual-config :file-config)]
    (logger/info host)
;;    (swap! db/db* assoc-in [:settings :nrepl-host] host)
    (.setNreplHost (.getOptions configuration-base) host)
;;    (swap! db/db* assoc-in [:settings :mode] mode)
    (.setMode (.getOptions configuration-base) (name mode))
    (when-let [nrepl-port (parse-long (seesaw/text (seesaw/select editor [:#nrepl-port])))]
;;      (swap! db/db* assoc-in [:settings :nrepl-port] nrepl-port)
      (.setNreplPort (.getOptions configuration-base) (str nrepl-port))
;;      (swap! db/db* assoc-in [:settings :project] host)
      (.setProject (.getOptions configuration-base) project))))


   (defn ^:private build-editor-view []
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
                       (.setEnabled ^JTextField (seesaw/select panel [:#nrepl-host]) manual?)
                       (.setEnabled ^JTextField (seesaw/select panel [:#nrepl-port]) manual?))))

    panel))
    #_(defn -init []
      [[] (atom {:location "default"})])

    (defn -createEditor [_]
      (let [editor-view (build-editor-view)]
                       (reset! config.factory.base/editor-view* editor-view)
                       editor-view))
    (defn -applyEditorTo [_ configuration]
      (apply-editor-to configuration))

     (defn -resetEditorFrom [_ configuration] ())