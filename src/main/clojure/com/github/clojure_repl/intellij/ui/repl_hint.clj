(ns com.github.clojure-repl.intellij.ui.repl-hint
  (:import
   [com.intellij.codeInsight.hint HintManager]
   [com.intellij.openapi.editor Editor]))

(set! *warn-on-reflection* true)

(defn show-info
  [value ^Editor editor]
  (.showInformationHint (HintManager/getInstance) editor (str "=> " (or value "nil")) (HintManager/RIGHT)))

(defn show-error
  [error ^Editor editor]
  (.showErrorHint (HintManager/getInstance) editor (str "=> " error) (HintManager/RIGHT)))
