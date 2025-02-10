(ns com.github.clojure-repl.intellij.ui.color
  (:require
   [clojure.string :as string])
  (:import
   [com.intellij.openapi.editor.colors EditorColorsManager TextAttributesKey]
   [com.intellij.openapi.editor.markup TextAttributes]
   [com.intellij.ui JBColor]
   [com.intellij.util.ui UIUtil]
   [java.awt Color]))

(set! *warn-on-reflection* true)

(defn ^:private attr ^TextAttributesKey [& {:keys [key foreground background effect effect-type font-type]
                                            :or {font-type 0}}]
  (TextAttributesKey/createTextAttributesKey
   ^String key
   (TextAttributes. foreground background effect effect-type font-type)))

(defn text-attributes []
  {:repl-window (attr :key "REPL_WINDOW"
                      :background (JBColor/background))
   :eval-inline-hint (attr :key "REPL_EVAL_INLINE_INLAY_HINT"
                           :background (JBColor. (Color/decode "#c7e8fc") (Color/decode "#16598c")))
   :test-summary-label (attr :key "REPL_TEST_SUMMARY_LABEL"
                             :foreground JBColor/GRAY)
   :test-result-error (attr :key "REPL_TEST_RESULT_ERROR"
                            :foreground JBColor/RED)
   :test-result-fail (attr :key "REPL_TEST_RESULT_FAIL"
                           :foreground JBColor/RED)
   :test-result-pass (attr :key "REPL_TEST_RESULT_PASS"
                           :foreground (UIUtil/getToolTipForeground))})

(defn ^:private global-attribute-for ^TextAttributes [key]
  (.. (EditorColorsManager/getInstance)
      getGlobalScheme
      (getAttributes (key (text-attributes)))))

(defn test-result-pass ^TextAttributes [] (global-attribute-for :test-result-pass))
(defn test-result-fail ^TextAttributes [] (global-attribute-for :test-result-fail))
(defn test-result-error ^TextAttributes [] (global-attribute-for :test-result-error))
(defn test-summary-label ^TextAttributes [] (global-attribute-for :test-summary-label))
(defn repl-window ^TextAttributes [] (global-attribute-for :repl-window))
(defn eval-inline-hint ^TextAttributes [] (global-attribute-for :eval-inline-hint))

(defn remove-ansi-color ^String [text]
  ;; TODO support ANSI colors for libs like matcher-combinators pretty prints.
  (string/replace text #"\u001B\[[;\d]*m" ""))
