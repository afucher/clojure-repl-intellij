(ns com.github.clojure-repl.intellij.ui.components
  (:require
   [com.github.clojure-repl.intellij.keyboard-manager :as key-manager]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [seesaw.core :as seesaw]
   [seesaw.layout :as s.layout]
   [seesaw.mig :as mig])
  (:import
   [com.intellij.icons AllIcons$General]
   [com.intellij.openapi.editor EditorFactory]
   [com.intellij.openapi.editor.ex EditorEx]
   [com.intellij.openapi.fileTypes FileTypeManager]
   [com.intellij.ui EditorTextField IdeBorderFactory]
   [java.awt Font]
   [java.awt.event ActionEvent]
   [javax.swing JButton JComponent]))

(set! *warn-on-reflection* true)

(defn clojure-text-field ^EditorTextField
  [& {:keys [id background-color text project editable? font on-key-pressed]
      :or {editable? true}}]
  (let [document (.createDocument (EditorFactory/getInstance) ^String text)
        clojure-file-type (.getStdFileType (FileTypeManager/getInstance) "clojure")
        editor-text-field (EditorTextField. document project clojure-file-type (not editable?) false)]
    (when id (seesaw/config! editor-text-field :id id))
    (when background-color (.setBackground editor-text-field background-color))
    (when font (.setFont editor-text-field font))
    (when on-key-pressed (.putClientProperty editor-text-field :on-key-pressed on-key-pressed))
    editor-text-field))

(defn init-clojure-text-field!
  "Some fields like .getEditor are initialized async,
   so we call this only when we know the Editor is available"
  [^EditorTextField editor-text-field]
  (when-let [on-key-pressed (.getClientProperty editor-text-field :on-key-pressed)]
    (app-manager/invoke-later!
     {:invoke-fn (fn []
                   (.setVerticalScrollbarVisible ^EditorEx (.getEditor editor-text-field) true)
                   (key-manager/register-listener-for-editor!
                    {:editor (.getEditor editor-text-field)
                     :on-key-pressed on-key-pressed}))})))

(defn collapsible ^JComponent
  [& {:keys [collapsed-title expanded-title content ^Font title-font]}]
  (let [content-panel (seesaw/vertical-panel :visible? false :items [])
        toggle ^JButton (seesaw/toggle
                         :text collapsed-title
                         :icon AllIcons$General/ArrowRight
                         :selected? false)]
    (when title-font (.setFont toggle title-font))
    (.setBorder toggle (IdeBorderFactory/createBorder (.getBackground toggle)))
    (seesaw/listen toggle :action (fn [_]
                                    (let [toggled? (seesaw/config toggle :selected?)
                                          icon (if toggled? AllIcons$General/ArrowDown AllIcons$General/ArrowRight)
                                          text (if toggled? expanded-title collapsed-title)]
                                      (seesaw/config! toggle
                                                      :icon icon
                                                      :text text)
                                      (seesaw/config! content-panel :visible? toggled?)
                                      (when toggled?
                                        (seesaw/config! content-panel :items [(content)])))))
    (mig/mig-panel :constraints ["insets 0"]
                   :items [[toggle "span"]
                           [content-panel "gapx 10"]])))

(defn add-field-to-multi-field! [ui group-id initial-text]
  (let [group ^JComponent (seesaw/select ui [(keyword (str "#" (name group-id)))])
        prefix (.getClientProperty group :prefix-id)
        columns (.getClientProperty group :columns)
        new-id (.getComponentCount group)
        text-id (keyword (str prefix new-id))
        text-ui (seesaw/text :id text-id :text initial-text :columns columns)]
    (s.layout/add-widget group text-ui)
    (s.layout/handle-structure-change group)))

(defn multi-field ^JComponent
  [& {:keys [label group-id button-id prefix-id initial-text columns]
      :or {columns 15}}]
  (let [group-component ^JComponent (mig/mig-panel
                                     :constraints ["gap 0"]
                                     :id group-id
                                     :items [])]
    (.putClientProperty group-component :columns columns)
    (.putClientProperty group-component :prefix-id prefix-id)
    [[(seesaw/label label) ""]
     [group-component "gap 0"]
     [(seesaw/button
       :id button-id
       :size [36 :by 36]
       :text "+"
       :listen [:action (fn [^ActionEvent event]
                          (let [button (.getSource event)
                                ui (.getParent ^JComponent button)]
                            (add-field-to-multi-field! ui group-id initial-text)))]) "gap 0, wrap"]]))

(defn field-values-from-multi-field [ui group-id]
  (->> (.getComponents ^JComponent (seesaw/select ui [(keyword (str "#" (name group-id)))]))
       (mapv seesaw/text)
       (filterv not-empty)))
