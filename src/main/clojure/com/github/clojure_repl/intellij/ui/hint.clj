(ns com.github.clojure-repl.intellij.ui.hint
  (:import
   [com.intellij.codeInsight.hint HintManager]
   [com.intellij.openapi.editor Editor]))

(set! *warn-on-reflection* true)

(defn show-info
  [& {:keys [message editor prefix]
      :or {prefix ""}}]
  (.showInformationHint (HintManager/getInstance) ^Editor editor (str prefix message) HintManager/RIGHT))

(defn show-error
  [& {:keys [message editor prefix]
      :or {prefix ""}}]
  (.showErrorHint (HintManager/getInstance) ^Editor editor (str prefix message) HintManager/RIGHT))

(defn show-success
  [& {:keys [message editor prefix]
      :or {prefix ""}}]
  (.showInformationHint (HintManager/getInstance) ^Editor editor (str prefix message) HintManager/RIGHT))

(defn show-repl-info [& {:as args}]
  (show-info (assoc args :prefix "=> " :message (or (:message args) "nil"))))

(defn show-repl-error [& {:as args}]
  (show-error (assoc args :prefix "=> ")))
