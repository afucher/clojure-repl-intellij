(ns com.github.ericdallo.clj4intellij.config
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
    (edn/read-string (slurp (io/resource "META-INF/clj4intellij.edn")))
    (catch Exception _ nil)))

(def ^:private config (memoize-if-not-nil config*))

(defn ^:private plugin* []
  (try
    (slurp (io/resource "META-INF/plugin.xml"))
    (catch Exception _ nil)))

(def ^:private plugin (memoize-if-not-nil plugin*))

(defn ^:private plugin-name* []
  (when-let [plugin (plugin)]
    (last (re-find #"<name>(.+)</name>" plugin))))

(defn nrepl-support? []
  (-> (config) :nrepl))

(defn nrepl-port []
  (-> (config) :nrepl :port))

(def plugin-name (memoize-if-not-nil plugin-name*))
