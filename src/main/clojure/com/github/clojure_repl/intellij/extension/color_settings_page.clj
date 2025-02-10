(ns com.github.clojure-repl.intellij.extension.color-settings-page
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.ColorSettingsPage
   :implements [com.intellij.openapi.options.colors.ColorSettingsPage])
  (:require
   [com.github.clojure-repl.intellij.ui.color :as ui.color])
  (:import
   [com.intellij.lang Language]
   [com.intellij.openapi.editor.colors TextAttributesKey]
   [com.intellij.openapi.fileTypes SyntaxHighlighterFactory]
   [com.intellij.openapi.options.colors AttributesDescriptor ColorDescriptor]))

(set! *warn-on-reflection* true)

(defn -getDisplayName [_] "Clojure REPL")

(def ^:private title-by-text-attribute
  {:repl-window "REPL window"
   :eval-inline-hint "Eval inline inlay hint"
   :test-summary-label "Test summary//Label"
   :test-result-error "Test summary//Error"
   :test-result-fail "Test summary//Fail"
   :test-result-pass "Test summary//Pass"})

(defn -getAttributeDescriptors [_]
  (->> (ui.color/text-attributes)
       (mapv #(AttributesDescriptor. ^String (title-by-text-attribute (first %)) ^TextAttributesKey (second %)))
       (into-array AttributesDescriptor)))

(defn -getColorDescriptors [_] ColorDescriptor/EMPTY_ARRAY)

(defn -getHighlighter [_]
  (let [language (or (Language/findLanguageByID "clojure")
                     (Language/findLanguageByID "textmate"))]
    (SyntaxHighlighterFactory/getSyntaxHighlighter language nil nil)))

(defn -getAdditionalHighlightingTagToDescriptorMap [_]
  (update-keys (ui.color/text-attributes) name))

(defn -getDemoText [_]
  "
(+ 1 2) <eval-inline-hint>3</eval-inline-hint>
\"Hello World\" <eval-inline-hint>\"Hello World\"</eval-inline-hint>
")

(defn -getPreviewEditorCustomizer [_])
(defn -customizeColorScheme [_ scheme] scheme)
(defn -getAdditionalInlineElementToDescriptorMap [_])
(defn -getAdditionalHighlightingTagToColorKeyMap [_])
