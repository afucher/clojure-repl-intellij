(ns com.github.clojure-repl.intellij.ui.components
  (:require
   [com.github.clojure-repl.intellij.keyboard-manager :as key-manager]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.intellij.icons AllIcons$General]
   [com.intellij.openapi.editor EditorFactory]
   [com.intellij.openapi.editor.ex EditorEx]
   [com.intellij.openapi.fileTypes FileTypeManager]
   [com.intellij.ui EditorTextField IdeBorderFactory]
   [java.awt Font]
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
                                    (let [toggled? (seesaw/config toggle :selected?)]
                                      (seesaw/config! toggle
                                                      :icon (if toggled? AllIcons$General/ArrowDown AllIcons$General/ArrowRight)
                                                      :text (if toggled? expanded-title collapsed-title))
                                      (seesaw/config! content-panel :visible? toggled?)
                                      (when toggled?
                                        (seesaw/config! content-panel :items [(content)])))))
    (mig/mig-panel :constraints ["insets 0"]
                   :items [[toggle "span"]
                           [content-panel "gapx 10"]])))
