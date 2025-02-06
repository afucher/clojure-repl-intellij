(ns com.github.clojure-repl.intellij.ui.color
  (:require
   [clojure.string :as string])
  (:import
   [com.intellij.ui JBColor]
   [com.intellij.util.ui UIUtil]
   [java.awt Color]))

(set! *warn-on-reflection* true)

;; TODO refactor this ns to use textAttributes with customizable color schemes
(defn normal-foreground [] (UIUtil/getToolTipForeground))
(def fail-foreground JBColor/RED)
(def error-foreground JBColor/RED)
(def editor-background-color (JBColor/background))
(def low-light-foreground JBColor/GRAY)

(def eval-inline-hint-background (JBColor. (Color/decode "#c7e8fc") (Color/decode "#16598c")))

(defn remove-ansi-color ^String [text]
  ;; TODO support ANSI colors for libs like matcher-combinators pretty prints.
  (string/replace text #"\u001B\[[;\d]*m" ""))
