(ns com.github.clojure-repl.intellij.extension.inlay-esc-handler
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.InlayEscHandler
   :extends com.intellij.openapi.editor.actionSystem.EditorActionHandler)
  (:import
   [com.intellij.openapi.editor Caret Editor Inlay]))

(set! *warn-on-reflection* true)

(defn -doExecute [_ ^Editor editor ^Caret _caret _data-context]
  (when-let [inline-class (try (Class/forName "com.github.clojure_repl.intellij.ui.inlay_hint.EvalInlayHintRenderer") (catch Exception _ nil))]
    (doseq [^Inlay inlay (.getAfterLineEndElementsInRange
                          (.getInlayModel editor)
                          0
                          (.. editor getDocument getTextLength)
                          inline-class)]
      (.dispose inlay))))
