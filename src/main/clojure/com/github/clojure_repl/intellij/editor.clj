(ns com.github.clojure-repl.intellij.editor
  (:import
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.util.text StringUtil]))

(set! *warn-on-reflection* true)

(defn editor->cursor-position [^Editor editor]
  (let [offset (.. editor getCaretModel getCurrentCaret getOffset)
        text (.getCharsSequence (.getDocument editor))
        line-col (StringUtil/offsetToLineColumn text offset)]
    [(.line line-col) (.column line-col)]))
