(ns com.github.clojure-repl.intellij.ui.components
  (:require
   [com.github.clojure-repl.intellij.keyboard-manager :as key-manager]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [seesaw.core :as seesaw])
  (:import
   [com.intellij.openapi.editor EditorFactory]
   [com.intellij.openapi.editor.ex EditorEx]
   [com.intellij.openapi.fileTypes FileTypeManager]
   [com.intellij.ui EditorTextField]))

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
