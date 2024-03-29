(ns com.github.clojure-repl.intellij.ui.color
  (:import
   [com.intellij.ui JBColor]
   [com.intellij.util.ui UIUtil]))

(def normal-foreground (UIUtil/getToolTipForeground))
(def fail-foreground JBColor/RED)
(def error-foreground JBColor/RED)

(def low-light-foreground JBColor/GRAY)
