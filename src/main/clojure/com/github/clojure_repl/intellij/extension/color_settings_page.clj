(ns com.github.clojure-repl.intellij.extension.color-settings-page
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.ColorSettingsPage
   :implements [com.intellij.openapi.options.colors.ColorSettingsPage])
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.ui.color :as ui.color])
  (:import
   [com.intellij.lang Language]
   [com.intellij.openapi.editor.colors TextAttributesKey]
   [com.intellij.openapi.fileTypes SyntaxHighlighterFactory]
   [com.intellij.openapi.options.colors AttributesDescriptor ColorDescriptor]))

(set! *warn-on-reflection* true)

(defn -getDisplayName [_] "Clojure REPL")

(defn ^:private title-by-text-attribute []
  (merge (reduce (fn [m k]
                   (let [display-name (-> (name k)
                                          (string/replace "--" "//")
                                          (string/replace "-" " ")
                                          string/capitalize)]
                     (assoc m k display-name)))
                 {}
                 (keys (ui.color/text-attributes)))
         {:repl-window "REPL window"}))

(defn -getAttributeDescriptors [_]
  (->> (ui.color/text-attributes)
       (mapv #(AttributesDescriptor. ^String ((title-by-text-attribute) (first %)) ^TextAttributesKey (second %)))
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
