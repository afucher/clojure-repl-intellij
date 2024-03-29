(ns com.github.clojure-repl.intellij.ui.hint
  (:require
   [com.github.clojure-repl.intellij.app-info :as app-info])
  (:import
   [com.intellij.codeInsight.hint
    HintManager]
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

(set! *warn-on-reflection* false)
(defn show-success
  [& {:keys [message ^Editor editor prefix]
      :or {prefix ""}}]
  (if (app-info/at-least-version?  "233.9802.14")
    (.showSuccessHint (HintManager/getInstance) ^Editor editor (str prefix message) HintManager/RIGHT)
    (.showInformationHint (HintManager/getInstance) ^Editor editor (str prefix message) HintManager/RIGHT)))
(set! *warn-on-reflection* true)

(defn show-repl-info [& {:as args}]
  (show-info (assoc args :prefix "=> " :message (or (:message args) "nil"))))

(defn show-repl-error [& {:as args}]
  (show-error (assoc args :prefix "=> ")))
