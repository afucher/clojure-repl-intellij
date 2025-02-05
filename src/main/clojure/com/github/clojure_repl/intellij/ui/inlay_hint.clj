(ns com.github.clojure-repl.intellij.ui.inlay-hint
  (:require
   [com.github.clojure-repl.intellij.ui.color :as ui.color]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.ide.highlighter HighlighterFactory]
   [com.intellij.lang Language]
   [com.intellij.openapi.editor Editor EditorCustomElementRenderer Inlay]
   [com.intellij.openapi.editor.colors EditorFontType]
   [com.intellij.openapi.editor.impl FontInfo]
   [com.intellij.openapi.editor.markup TextAttributes]
   [com.intellij.openapi.fileEditor FileDocumentManager]
   [com.intellij.openapi.fileTypes SyntaxHighlighterFactory]
   [com.intellij.ui SimpleColoredText SimpleTextAttributes]
   [com.intellij.util.ui GraphicsUtil UIUtil]
   [com.intellij.xdebugger.ui DebuggerColors]
   [java.awt Graphics Graphics2D Rectangle]))

(set! *warn-on-reflection* true)

(defn remove-in-current-line [^Editor editor]
  (let [inlays (.getAfterLineEndElementsForLogicalLine
                (.getInlayModel editor)
                (.. editor getCaretModel getLogicalPosition line))]
    (doseq [^Inlay inlay inlays]
      (.dispose inlay))))

(defn ^:private simple-colored-text ^SimpleColoredText [^Editor editor ^String text]
  (let [scheme (.getColorsScheme editor)
        language (Language/findLanguageByID "clojure")
        v-file (.getFile (FileDocumentManager/getInstance) (.getDocument editor))
        syntax-highlighter (SyntaxHighlighterFactory/getSyntaxHighlighter language (.getProject editor) v-file)
        highlighter (HighlighterFactory/createHighlighter syntax-highlighter scheme)]
    (.setText highlighter text)
    (let [colored-text (SimpleColoredText.)
          iterator (.createIterator highlighter 0)]
      (while (not (.atEnd iterator))
        (let [start (.getStart iterator)
              end (.getEnd iterator)
              keys (.getTextAttributesKeys iterator)
              attributes (if (seq keys)
                           (SimpleTextAttributes/fromTextAttributes (.getAttributes scheme (first keys)))
                           SimpleTextAttributes/REGULAR_ATTRIBUTES)
              token-text (subs text start end)]
          (.append colored-text token-text attributes))
        (.advance iterator))
      colored-text)))

(defn ^:private paint
  [^String text ^Inlay inlay ^Graphics2D g ^Rectangle r ^TextAttributes _text-attributes]
  (let [editor (.getEditor inlay)
        colors-scheme (.getColorsScheme editor)
        attrs (.getAttributes colors-scheme DebuggerColors/INLINED_VALUES_EXECUTION_LINE)
        font-style (if attrs (.getFontType attrs) 0)
        font (UIUtil/getFontWithFallback (.getFont colors-scheme (EditorFontType/forJavaStyle font-style)))
        font-metrics (FontInfo/getFontMetrics font (FontInfo/getFontRenderContext (.getContentComponent editor)))
        space (.charWidth font-metrics \s)
        margin (/ space 3)
        gap 1
        background-x (+ (.x r) space)
        text-x* (atom (int (+ background-x margin)))
        background-height (- (.height r) (* 2 gap))
        presentation (simple-colored-text editor text)]
    (.setFont g font)
    (let [config (GraphicsUtil/setupAAPainting g)]
      (GraphicsUtil/paintWithAlpha g 0.55)
      (.setColor g ui.color/eval-inline-hint-background)
      (.fillRoundRect g
                      background-x
                      (+ gap (.y r))
                      (/ (- (.width r) space) 2)
                      background-height
                      6
                      6)
      (.restore config))
    (doall
     (for [i (range (count (.getTexts presentation)))
           :let [cur-text ^String (nth (.getTexts presentation) i)
                 attr ^SimpleTextAttributes (nth (.getAttributes presentation) i)]]
       (do
         (.setColor g (.getFgColor attr))
         (.drawString g cur-text ^Integer @text-x* (+ (.y r) (.getAscent editor)))
         (reset! text-x* (+ @text-x* (.stringWidth font-metrics cur-text))))))))

(defn show-code [^String text ^Editor editor]
  (let [inlay-model (.getInlayModel editor)
        offset (.getOffset (.getCaretModel editor))]
    (remove-in-current-line editor)
    (.addAfterLineEndElement
     inlay-model
     offset
     false
     (proxy+ EvalInlayHintRenderer []
       EditorCustomElementRenderer
       (calcWidthInPixels [_ ^Inlay _inlay]
         (let [font-metrics (.getFontMetrics (.getComponent editor)
                                             (.getFont (.getColorsScheme editor)
                                                       EditorFontType/PLAIN))
               space (.charWidth font-metrics \s)]
           (* 2 (+ space (.stringWidth font-metrics text)))))
       (paint [_ ^Inlay inlay ^Graphics g ^Rectangle range ^TextAttributes text-attributes]
         (paint text inlay g range text-attributes))))))
