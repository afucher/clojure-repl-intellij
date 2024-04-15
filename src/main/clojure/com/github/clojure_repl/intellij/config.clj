(ns com.github.clojure-repl.intellij.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(defonce ^:private cache* (atom {}))

(defn ^:private memoize-if-not-nil [f]
  (fn []
    (if-let [cached-result (get @cache* f)]
      cached-result
      (if-let [result (f)]
        (do (swap! cache* assoc f result)
            result)
        nil))))

(defn ^:private config* []
  (try
    (edn/read-string (slurp (io/resource "META-INF/clojure-repl-intellij.edn")))
    (catch Exception _ nil)))

(def ^:private config (memoize-if-not-nil config*))

(defn nrepl-debug? []
  (-> (config) :nrepl-debug))
