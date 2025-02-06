(ns com.github.clojure-repl.intellij.extension.inlay-esc-handler
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.InlayEscHandler
   :extends com.intellij.openapi.editor.actionSystem.EditorActionHandler)
  (:require
   [com.github.clojure-repl.intellij.ui.inlay-hint :as ui.inlay-hint])
  (:import
   [com.intellij.openapi.editor Caret Editor]))

(set! *warn-on-reflection* true)

(defn -doExecute [_ ^Editor editor ^Caret _caret _data-context]
  (ui.inlay-hint/remove-all editor))
