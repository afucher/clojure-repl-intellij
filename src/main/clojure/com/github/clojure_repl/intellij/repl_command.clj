(ns com.github.clojure-repl.intellij.repl-command
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]))

(set! *warn-on-reflection* true)

(defn ^:private list-files [project-path]
  (.list (io/file project-path)))

(defn ^:private project->project-type [project-path]
  (some #(case %
           "project.clj" :lein
           "deps.edn" :clojure
           "bb.edn" :babashka
           "shadow-cljs.edn" :shadow-cljs
           "build.boot" :boot
           "nbb.edn" :nbb
           ("build.gradle" "build.gradle.kts") :gradle
           nil)
        (list-files project-path)))

(def ^:private project-type->command
  {:lein "lein"
   :clojure "clojure"
   :babashka "bb"
   :shadow-cljs "npx shadow-cljs"
   :boot "boot"
   :nbb "nbb"
   :gradle "./gradlew"})

(def ^:private middleware-versions
  {"nrepl/nrepl" "1.1.0"
   "cider/cider-nrepl" "0.45.0"})

(def ^:private project-type->parameters
  {:lein ["update-in" ":dependencies" "conj" "[nrepl/nrepl \"%nrepl/nrepl%\"]"
          "--" "update-in" ":plugins" "conj" "[cider/cider-nrepl \"%cider/cider-nrepl%\"]"
          "--" "repl" ":headless" ":host" "localhost"]
   :clojure ["-Sdeps"
             "{:deps {nrepl/nrepl {:mvn/version \"%nrepl/nrepl%\"} cider/cider-nrepl {:mvn/version \"%cider/cider-nrepl%\"}} :aliases {:cider/nrepl {:main-opts [\"-m\" \"nrepl.cmdline\" \"--middleware\" \"[cider.nrepl/cider-middleware]\"]}}}"
             "-M:cider/nrepl"]
   :babashka ["nrepl-server" "localhost:0"]
   :shadow-cljs ["server"]
   :boot ["repl" "-s" "-b" "localhost" "wait"]
   :nbb ["nrepl-server"]
   :gradle ["-Pdev.clojurephant.jack-in.nrepl=nrepl:nrepl:%nrepl/nrepl%,cider:cider-nrepl:%cider/cider-nrepl%"
            "clojureRepl"
            "--middleware=cider.nrepl/cider-middleware"]})

(defn ^:private replace-versions [middleware-versions parameters]
  (reduce
   (fn [params [middleware version]]
     (mapv #(string/replace % (str "%" middleware "%") version) params))
   parameters
   middleware-versions))

(defn project->repl-start-command [project-path]
  (let [project-type (project->project-type project-path)
        command (project-type->command project-type)
        parameters (replace-versions middleware-versions (project-type->parameters project-type))]
    ;; TODO Add support for customizing global options along with parameters like aliases
    ;; and dependencies injection, check how cider does for more details
    (concat [command] parameters)))
