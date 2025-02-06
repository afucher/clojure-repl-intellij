(ns com.github.clojure-repl.intellij.extension.inlay-listener-startup
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.InlayListenerStartup
   :implements [com.intellij.openapi.startup.StartupActivity
                com.intellij.openapi.project.DumbAware])
  (:require
   [com.github.clojure-repl.intellij.ui.inlay-hint :as ui.inlay-hint]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.openapi.editor EditorFactory]
   [com.intellij.openapi.editor.event EditorMouseEvent EditorMouseListener EditorMouseMotionListener]
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* true)

(defonce ^:private last-hovered-inlay* (atom nil))

(defn ^:private mouse-moved [^EditorMouseEvent event]
  (when @last-hovered-inlay*
    (when-not (= @last-hovered-inlay* (.getInlay event))
      (ui.inlay-hint/mark-inlay-hover-status @last-hovered-inlay* false))
    (reset! last-hovered-inlay* nil))
  (when-let [inlay (.getInlay event)]
    (when (= (ui.inlay-hint/renderer-class) (class (.getRenderer inlay)))
      (ui.inlay-hint/mark-inlay-hover-status inlay true)
      (reset! last-hovered-inlay* inlay))))

(defn ^:private mouse-released [^EditorMouseEvent event]
  (when-let [inlay (.getInlay event)]
    (when (= (ui.inlay-hint/renderer-class) (class (.getRenderer inlay)))
      (ui.inlay-hint/toggle-expand-inlay-hint inlay))))

(defn -runActivity [_this ^Project _project]
  (doto (.getEventMulticaster (EditorFactory/getInstance))
    (.addEditorMouseMotionListener
     (proxy+ [] EditorMouseMotionListener
       (mouseMoved [_ event] (mouse-moved event))))
    (.addEditorMouseListener
     (proxy+ [] EditorMouseListener
       (mousePressed [_ ^EditorMouseEvent _event])
       (mouseReleased [_ ^EditorMouseEvent event] (mouse-released event))))))
