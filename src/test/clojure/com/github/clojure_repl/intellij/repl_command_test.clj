(ns com.github.clojure-repl.intellij.repl-command-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.github.clojure-repl.intellij.repl-command :as repl-command]))

(def project-root "foo/bar/project")

(deftest project->repl-start-command-test
  (testing "clojure"
    (is (= ["clojure"
            "-Sdeps"
            "{:deps {nrepl/nrepl {:mvn/version \"1.1.0\"} cider/cider-nrepl {:mvn/version \"0.45.0\"}} :aliases {:cider/nrepl {:main-opts [\"-m\" \"nrepl.cmdline\" \"--middleware\" \"[cider.nrepl/cider-middleware]\"]}}}"
            "-M:cider/nrepl"]
           (with-redefs [repl-command/list-files (constantly ["deps.edn" "src"])]
             (repl-command/project->repl-start-command project-root)))))
  (testing "lein"
    (is (= ["lein" "update-in" ":dependencies" "conj" "[nrepl/nrepl \"1.1.0\"]"
            "--" "update-in" ":plugins" "conj" "[cider/cider-nrepl \"0.45.0\"]"
            "--" "repl" ":headless" ":host" "localhost"]
           (with-redefs [repl-command/list-files (constantly ["project.clj" "src"])]
             (repl-command/project->repl-start-command project-root)))))
  (testing "babashka"
    (is (= ["bb" "nrepl-server" "localhost:0"]
           (with-redefs [repl-command/list-files (constantly ["bb.edn" "src"])]
             (repl-command/project->repl-start-command project-root)))))
  (testing "shadow-cljs"
    (is (= ["npx shadow-cljs" "server"]
           (with-redefs [repl-command/list-files (constantly ["shadow-cljs.edn" "src"])]
             (repl-command/project->repl-start-command project-root)))))
  (testing "boot"
    (is (= ["boot" "repl" "-s" "-b" "localhost" "wait"]
           (with-redefs [repl-command/list-files (constantly ["build.boot" "src"])]
             (repl-command/project->repl-start-command project-root)))))
  (testing "nbb"
    (is (= ["nbb" "nrepl-server"]
           (with-redefs [repl-command/list-files (constantly ["nbb.edn" "src"])]
             (repl-command/project->repl-start-command project-root)))))
  (testing "gradle"
    (is (= ["./gradlew"
            "-Pdev.clojurephant.jack-in.nrepl=nrepl:nrepl:1.1.0,cider:cider-nrepl:0.45.0"
            "clojureRepl"
            "--middleware=cider.nrepl/cider-middleware"]
           (with-redefs [repl-command/list-files (constantly ["build.gradle" "src"])]
             (repl-command/project->repl-start-command project-root))))))
