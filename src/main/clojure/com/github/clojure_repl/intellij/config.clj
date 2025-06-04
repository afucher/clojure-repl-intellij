(ns com.github.clojure-repl.intellij.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [rewrite-clj.zip :as z])
  (:import
   [com.intellij.ide.plugins PluginManagerCore]
   [com.intellij.openapi.extensions PluginId]
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.vfs VfsUtilCore VirtualFile]
   [java.io File]
   [java.util.jar JarEntry]))

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

(defn safe-read-edn-string
  "Uses rewrite-clj to read the EDN from a raw string. This function is necessary because the EDN contains source code
   with reader macros and variables prefixed with $, which are intended for code replacement and evaluation. These elements
   make the EDN invalid for standard reading. By using rewrite-clj, we can parse the user's EDN, extract the code snippets,
   and transform them into strings for further processing."
  [raw-string]
  (try
    (-> raw-string
        z/of-string
        (z/get :eval-code-actions)
        z/down
        (->> (iterate z/right)
             (take-while (complement z/end?))
             (map (fn [a] {:name (-> a (z/get :name) z/sexpr)
                           :code (-> a (z/get :code) z/string)}))
             doall))
    (catch Throwable e
      (logger/error "Error parsing EDN:" e)
      [])))

(defn read-file-from-project-root [relative-path ^Project project]
  (let [^VirtualFile base-dir (.getBaseDir project) ; raiz do projeto
        ^VirtualFile file (.findFileByRelativePath base-dir relative-path)
        io-file (some-> file VfsUtilCore/virtualToIoFile)]
    io-file))

(defn ^:private read-configs-from-jar [^String jar-path]
  (try
    (with-open [jar-file (java.util.jar.JarFile. jar-path)]
      (let [entries (enumeration-seq (.entries jar-file))
            config-paths (filter #(and (.startsWith (.getName ^JarEntry %) "clj-repl-intellij.exports/")
                                       (= "config.edn" (.getName (io/file (.getName ^JarEntry %)))))
                                 entries)]
        (mapv (fn [entry]
                (with-open [stream (.getInputStream jar-file entry)]
                  (safe-read-edn-string (slurp stream))))
              config-paths)))
    (catch Throwable e
      (logger/error (str "Error reading config from JAR: " jar-path) e)
      [])))

(defn config-from-classpath*
  "Reads all config.edn files from JARs in the project's classpath that are under clj-repl-intellij.exports directory.
   Returns a sequence of parsed config maps."
  [^Project project]
  (let [classpath (db/get-in project [:classpath])
        jar-paths (filter #(.endsWith ^String % ".jar") classpath)]
    (->> jar-paths
         (mapcat read-configs-from-jar)
         flatten
         (remove empty?))))

(defn ^:private config-from-project* [^Project project]
  (when-let [config-file ^File (read-file-from-project-root ".clj-repl-intellij/config.edn" project)]
    (when (.exists config-file)
      (safe-read-edn-string (slurp config-file)))))

(defn ^:private config-from-user* []
  (let [config-file (io/file (str (System/getProperty "user.home") "/.config/clj-repl-intellij/config.edn"))]
    (when (.exists config-file)
      (safe-read-edn-string (slurp config-file)))))

;;global config
(defn from-user []
  (config-from-user*))

(defn eval-code-actions-from-user []
  (from-user))

(defn from-project [^Project project]
  (concat
   (config-from-project* project)
   (config-from-classpath* project)))


(comment
  (-> (first (db/all-projects))
      from-project))
