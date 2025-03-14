(ns com.github.clojure-repl.intellij.editor
  (:require
   [com.github.clojure-repl.intellij.parser :as parser]
   [rewrite-clj.zip :as z])
  (:import
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.fileEditor FileDocumentManager]))

(set! *warn-on-reflection* true)

(defn editor->url [^Editor editor]
  ;; TODO sanitize URL, encode, etc
  (.getUrl (.getFile (FileDocumentManager/getInstance) (.getDocument editor))))

(defn ns-form [^Editor editor]
  (-> editor
      .getDocument
      .getText
      z/of-string
      parser/find-namespace
      z/up))
