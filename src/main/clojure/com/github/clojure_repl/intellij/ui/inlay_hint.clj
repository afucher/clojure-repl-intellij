(ns com.github.clojure-repl.intellij.ui.inlay-hint
  (:require
   [clojure.edn :as edn]
   [clojure.pprint :as pprint]
   [com.github.clojure-repl.intellij.ui.color :as ui.color]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.ide.highlighter HighlighterFactory]
   [com.intellij.ide.ui.laf.darcula DarculaUIUtil]
   [com.intellij.lang Language]
   [com.intellij.openapi.editor
    Editor
    EditorCustomElementRenderer
    EditorFactory
    Inlay]
   [com.intellij.openapi.editor.colors EditorFontType]
   [com.intellij.openapi.editor.impl EditorImpl FontInfo]
   [com.intellij.openapi.editor.markup TextAttributes]
   [com.intellij.openapi.fileEditor FileDocumentManager]
   [com.intellij.openapi.fileTypes FileTypeManager SyntaxHighlighterFactory]
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.ui.popup JBPopupFactory]
   [com.intellij.ui EditorTextField SimpleColoredText SimpleTextAttributes]
   [com.intellij.util.ui GraphicsUtil JBUI$CurrentTheme$ActionButton UIUtil]
   [com.intellij.xdebugger.ui DebuggerColors]
   [java.awt
    Cursor
    Font
    FontMetrics
    Graphics
    Graphics2D
    Rectangle
    RenderingHints]
   [java.awt.geom RoundRectangle2D$Float]))

(set! *warn-on-reflection* true)

(defonce ^:private inlays* (atom {}))

(defn renderer-class [] (try (Class/forName "com.github.clojure_repl.intellij.ui.inlay_hint.EvalInlineInlayHintRenderer") (catch Exception _ nil)))

(defn ^:private font+metrics [^Inlay inlay]
  (let [editor (.getEditor inlay)
        colors-scheme (.getColorsScheme editor)
        attrs (.getAttributes colors-scheme DebuggerColors/INLINED_VALUES_EXECUTION_LINE)
        font-style (if attrs (.getFontType attrs) 0)
        font (UIUtil/getFontWithFallback (.getFont colors-scheme (EditorFontType/forJavaStyle font-style)))
        metrics (FontInfo/getFontMetrics font (FontInfo/getFontRenderContext (.getContentComponent editor)))]
    [font metrics]))

(defn ^:private clojure-colored-text ^SimpleColoredText
  [^Editor editor ^String text]
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

(defn ^:private paint-clojure-text
  [^Graphics2D g ^String text ^Rectangle r ^Editor editor ^FontMetrics font-metrics {:keys [background-x margin]}]
  (let [text-x* (atom (int (+ background-x margin)))
        presentation (clojure-colored-text editor text)]
    (doall
     (for [i (range (count (.getTexts presentation)))
           :let [cur-text ^String (nth (.getTexts presentation) i)
                 attr ^SimpleTextAttributes (nth (.getAttributes presentation) i)]]
       (do
         (.setColor g (.getFgColor attr))
         (.drawString g cur-text ^Integer @text-x* (+ (.y r) (.getAscent editor)))
         (reset! text-x* (+ @text-x* (.stringWidth font-metrics cur-text))))))))

(defn ^:private remove-in-cur-line [^Editor editor]
  (let [inlays (.getAfterLineEndElementsForLogicalLine
                (.getInlayModel editor)
                (.. editor getCaretModel getLogicalPosition line))]
    (doseq [^Inlay inlay inlays]
      (.dispose inlay)
      (swap! inlays* dissoc (.getRenderer inlay)))))

(defn ^:private remove-inlay [^Inlay inlay]
  (.dispose inlay)
  (swap! inlays* dissoc (.getRenderer inlay)))

(defn ^:private paint-background-rect
  [^Graphics2D g {:keys [background-x background-y background-width background-height]}]
  (let [config (GraphicsUtil/setupAAPainting g)]
    (GraphicsUtil/paintWithAlpha g 0.55)
    (.setColor g ui.color/eval-inline-hint-background)
    (.fillRoundRect g
                    background-x
                    background-y
                    background-width
                    background-height
                    6
                    6)
    (.restore config)))

(defn ^:private paint-hovered-background [^Graphics2D g {:keys [background-x background-y background-width background-height]}]
  (let [^Graphics2D g2 (.create g)
        arc (.getFloat DarculaUIUtil/BUTTON_ARC)]
    (.setRenderingHint g2 RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
    (.setRenderingHint g2 RenderingHints/KEY_STROKE_CONTROL RenderingHints/VALUE_STROKE_NORMALIZE)
    (.setColor g2 (JBUI$CurrentTheme$ActionButton/hoverBackground))
    (.fill g2 (RoundRectangle2D$Float. background-x background-y background-width background-height arc arc))
    (.dispose g2)))

(defn ^:private paint
  [^String text ^Inlay inlay ^Graphics2D g ^Rectangle r ^TextAttributes _text-attributes]
  (let [editor (.getEditor inlay)
        [font metrics] (font+metrics inlay)
        space (.charWidth ^FontMetrics metrics \s)
        gap 1
        ui-opts {:space space
                 :gap gap
                 :margin (/ space 3)
                 :background-x (+ (.x r) space)
                 :background-y (+ gap (.y r))
                 :background-width (- (.width r) space)
                 :background-height (- (.height r) (* 2 gap))}

        {:keys [hovering?]} (get @inlays* (.getRenderer inlay))]
    (.setFont g font)
    (paint-background-rect g ui-opts)
    (when hovering?
      (paint-hovered-background g ui-opts))
    (paint-clojure-text g text r editor metrics ui-opts)))

(defn ^:private create-renderer [^String text ^Editor editor]
  (let [inlay-model (.getInlayModel editor)
        offset (.getOffset (.getCaretModel editor))
        display-limit 100
        expandable? (> (count text) display-limit)
        summary-text (if expandable? (str (subs text 0 display-limit) " ...") text)
        renderer (proxy+ EvalInlineInlayHintRenderer []
                   EditorCustomElementRenderer
                   (calcWidthInPixels [_ ^Inlay _inlay]
                     (let [font-metrics (.getFontMetrics (.getComponent editor)
                                                         (.getFont (.getColorsScheme editor)
                                                                   EditorFontType/PLAIN))
                           space (.charWidth font-metrics \s)
                           margin (/ space 2)]
                       (+ margin space (.stringWidth font-metrics summary-text))))
                   (paint [_ ^Inlay inlay ^Graphics g ^Rectangle r ^TextAttributes text-attributes]
                     (paint summary-text inlay g r text-attributes)))]
    (remove-in-cur-line editor)
    (.addAfterLineEndElement inlay-model offset true renderer)
    renderer))

(defn ^:private pretty-printed-clojure-text [text]
  (try
    (with-out-str (pprint/pprint (edn/read-string {:default (fn [tag value]
                                                              (symbol (str "#" tag value)))} text)))
    (catch Exception e
      (logger/warn "Can't parse clojure code for eval block" e)
      text)))

(defn ^:private code-component [^String code ^Font font ^Project project]
  (let [document (.createDocument (EditorFactory/getInstance) code)
        clojure-file-type (.getStdFileType (FileTypeManager/getInstance) "clojure")
        field (EditorTextField. document project clojure-file-type true false)]
    (.setFont field font)
    field))

(defn remove-all [^Editor editor]
  (when-let [renderer-class (renderer-class)]
    (doseq [^Inlay inlay (.getAfterLineEndElementsInRange
                           (.getInlayModel editor)
                           0
                           (.. editor getDocument getTextLength)
                           renderer-class)]
      (remove-inlay inlay))))

(defn mark-inlay-hover-status [^Inlay inlay hovered?]
  (when (get-in @inlays* [(.getRenderer inlay) :expandable?])
    (let [cursor (if hovered? (Cursor/getPredefinedCursor Cursor/HAND_CURSOR) nil)]
      (swap! inlays* assoc-in [(.getRenderer inlay) :hovering?] hovered?)
      (.setCustomCursor ^EditorImpl (.getEditor inlay) (renderer-class) cursor)
      (.update inlay))))

(defn toggle-expand-inlay-hint [^Inlay inlay]
  (let [{:keys [expandable? text]} (get @inlays* (.getRenderer inlay))
        [font _] (font+metrics inlay)
        editor (.getEditor inlay)]
    (when expandable?
      (let [component (code-component text font (.. inlay getEditor getProject))
            popup (.createPopup
                   (doto
                    (.createComponentPopupBuilder (JBPopupFactory/getInstance) component component)
                     (.setResizable false)
                     (.setMovable false)))]
        (.showInBestPositionFor popup editor)))))

(defn show-code [^String text ^Editor editor]
  (remove-in-cur-line editor)
  (let [display-limit 100 ;; Move to customizable config
        expandable? (> (count text) display-limit)
        summary-text (if expandable? (str (subs text 0 display-limit) " ...") text)
        renderer (create-renderer text editor)]
    (swap! inlays* assoc renderer {:hovering? false
                                   :expandable? expandable?
                                   :text (pretty-printed-clojure-text text)
                                   :summary-text summary-text})))
