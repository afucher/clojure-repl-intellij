(ns com.github.clojure-repl.intellij.ui.text
  (:require
   [clojure.edn :as edn]
   [clojure.pprint :as pprint]
   [clojure.string :as string]
   [com.github.ericdallo.clj4intellij.logger :as logger]))

(defn pretty-printed-clojure-text [text]
  (try
    (with-out-str (pprint/pprint (edn/read-string {:default (fn [tag value]
                                                              (if (coll? value)
                                                                (pretty-printed-clojure-text value)
                                                                (symbol (str "#" tag value))))} text)))
    (catch Exception e
      (logger/warn "Can't parse clojure code for eval block" e)
      text)))

(defn remove-ansi-color ^String [text]
  ;; TODO support ANSI colors for libs like matcher-combinators pretty prints.
  (string/replace text #"\u001B\[[;\d]*m" ""))
