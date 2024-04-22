(ns com.github.clojure-repl.intellij.repl-command
  (:require
   [clojure.string :as string]))

(set! *warn-on-reflection* true)

(def ^:private project-type->command
  {:lein "lein"
   :clojure "clojure"
   :babashka "bb"
   :shadow-cljs "npx shadow-cljs"
   :boot "boot"
   :nbb "nbb"
   :gradle "./gradlew"})

(def ^:private middleware-versions
  {"nrepl/nrepl" "1.0.0"
   "cider/cider-nrepl" "0.45.0"})

(defn ^:private project-type->parameters [project-type aliases]
  (flatten
   (remove
    nil?
    (get
     {:lein ["update-in" ":dependencies" "conj" "[nrepl/nrepl \"%nrepl/nrepl%\"]"
             "--" "update-in" ":plugins" "conj" "[cider/cider-nrepl \"%cider/cider-nrepl%\"]"
             "--" (when (seq aliases)
                    ["with-profile" (str "+" (string/join ",+" aliases))]) "repl" ":headless" ":host" "localhost"]
      :clojure ["-Sdeps"
                "{:deps {nrepl/nrepl {:mvn/version \"%nrepl/nrepl%\"} cider/cider-nrepl {:mvn/version \"%cider/cider-nrepl%\"}} :aliases {:cider/nrepl {:main-opts [\"-m\" \"nrepl.cmdline\" \"--middleware\" \"[cider.nrepl/cider-middleware]\"]}}}"
                (str "-M:" (string/join ":" (conj aliases "cider/nrepl")))]
      :babashka ["nrepl-server" "localhost:0"]
      :shadow-cljs ["server"]
      :boot ["repl" "-s" "-b" "localhost" "wait"]
      :nbb ["nrepl-server"]
      :gradle ["-Pdev.clojurephant.jack-in.nrepl=nrepl:nrepl:%nrepl/nrepl%,cider:cider-nrepl:%cider/cider-nrepl%"
               "clojureRepl"
               "--middleware=cider.nrepl/cider-middleware"]}
     project-type))))

(defn ^:private replace-versions [middleware-versions parameters]
  (reduce
   (fn [params [middleware version]]
     (mapv #(string/replace % (str "%" middleware "%") version) params))
   parameters
   middleware-versions))

(defn project->repl-start-command [project-type aliases]
  (let [command (project-type->command project-type)
        parameters (replace-versions middleware-versions (project-type->parameters project-type aliases))]
    (concat [command] parameters)))
