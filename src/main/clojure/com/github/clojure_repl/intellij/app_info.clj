(ns com.github.clojure-repl.intellij.app-info
  (:import
   [com.intellij.openapi.application ApplicationInfo]
   [com.intellij.openapi.util BuildNumber]))

(defn at-least-version? [version]
  (>= (.compareTo (.withoutProductCode (.getBuild (ApplicationInfo/getInstance)))
                   (BuildNumber/fromString version))
      0))
