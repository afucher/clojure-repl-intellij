(ns com.github.clojure-repl.intellij.project
  (:require
   [clojure.java.io :as io])
  (:import
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* true)

(def type-by-file
  {"project.clj" :lein
   "deps.edn" :clojure
   "bb.edn" :babashka
   "shadow-cljs.edn" :shadow-cljs
   "build.boot" :boot
   "nbb.edn" :nbb
   "build.gradle" :gradle
   "build.gradle.kts" :gradle})

(def known-project-types (set (vals type-by-file)))

(def types
  (set (vals type-by-file)))

(defn ^:private list-files [project-path]
  (.list (io/file project-path)))

(defn project->project-type [^Project project]
  (some
   #(get type-by-file %)
   (list-files (.getBasePath project))))
