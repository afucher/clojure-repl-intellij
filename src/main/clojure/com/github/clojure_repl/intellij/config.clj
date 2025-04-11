(ns com.github.clojure-repl.intellij.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [com.github.ericdallo.clj4intellij.logger :as logger])
  (:import
   [com.intellij.ide.plugins PluginManagerCore]
   [com.intellij.openapi.extensions PluginId]
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.vfs VfsUtilCore VirtualFile]))

(set! *warn-on-reflection* true)

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

(defn plugin-version* []
  (.getVersion (PluginManagerCore/getPlugin (PluginId/getId "com.github.clojure-repl"))))

(def plugin-version (memoize plugin-version*))


;; USER CONFIGS - maybe create a namespace for this

(defn read-file-from-project-root [relative-path ^Project project]
  (let [^VirtualFile base-dir (.getBaseDir project) ; raiz do projeto
        ^VirtualFile file (.findFileByRelativePath base-dir relative-path)
        io-file (some-> file VfsUtilCore/virtualToIoFile)]
    io-file))


(defn safe-read-edn-string [raw-string]
  (try
    (->> raw-string
         (edn/read-string {:readers {'re re-pattern}}))
    (catch Exception e
      (logger/error e "Error reading edn string" raw-string))))

(def ^:dynamic *config*
  "Change this dynamic var to use custom config"
  {})

(defn ^:private config-from-classpath* []
  (io/resource "metrepl.exports/config.edn"))

(def ^:private config-from-classpath (memoize config-from-classpath*))

(defn ^:private config-from-envvar* []
  (some-> (System/getenv "CLJ_REPL_INTELLIJ_CONFIG")
          (safe-read-edn-string)))

(def ^:private config-from-envvar (memoize config-from-envvar*))

(defn ^:private config-from-project* [^Project project]
  (let [config-file (read-file-from-project-root ".clj-repl-intellij/config.edn" project)]
    (when config-file
      (safe-read-edn-string (slurp config-file)))))

(defn ^:private config-from-user* []
  (let [config-file (io/file (str (System/getProperty "user.home") "/.config/clj-repl-intellij/config.edn"))]
    (when config-file
      (safe-read-edn-string (slurp config-file)))))

(def ^:private config-from-user (memoize config-from-user*))

(defn all [^Project project]
  (merge-with into
              (config-from-user)
              (config-from-project* project)
              *config*))

(defn from-user []
  (merge-with into
              (config-from-user*)
              #_(config-from-envvar)
              *config*))

(defn eval-code-actions-from-user []
  (:eval-code-actions (from-user)))

(defn from-project [^Project project]
  (merge-with into
              (config-from-project* project)
              #_(config-from-classpath)
              *config*))
