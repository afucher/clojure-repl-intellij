(ns com.github.clojure-repl.intellij.ui.font
  (:import
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.editor.colors EditorColorsManager EditorFontType]
   [com.intellij.openapi.editor.markup TextAttributes]
   [com.intellij.util.ui UIUtil]
   [java.awt Font]))

(set! *warn-on-reflection* true)

(defn code-font ^Font
  ([^TextAttributes base-text-attr]
   (code-font base-text-attr nil))
  ([^TextAttributes base-text-attr ^Editor editor]
   (UIUtil/getFontWithFallback
    (.getFont (if editor
                (.getColorsScheme editor)
                (.getGlobalScheme (EditorColorsManager/getInstance)))
              (EditorFontType/forJavaStyle (.getFontType base-text-attr))))))
