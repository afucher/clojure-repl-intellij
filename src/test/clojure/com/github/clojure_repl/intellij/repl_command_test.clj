(ns com.github.clojure-repl.intellij.repl-command-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.github.clojure-repl.intellij.repl-command :as repl-command]))

(deftest project->repl-start-command-test
  (testing "clojure"
    (is (= ["clojure"
            "-Sdeps"
            "{:deps {nrepl/nrepl {:mvn/version \"1.3.1\"} cider/cider-nrepl {:mvn/version \"0.55.7\"}} :aliases {:cider/nrepl {:main-opts [\"-m\" \"nrepl.cmdline\" \"--middleware\" \"[cider.nrepl/cider-middleware]\"]}}}"
            "-M:cider/nrepl"]
           (repl-command/project->repl-start-command :clojure [] [])))
    (is (= ["clojure"
            "-Sdeps"
            "{:deps {nrepl/nrepl {:mvn/version \"1.3.1\"} cider/cider-nrepl {:mvn/version \"0.55.7\"}} :aliases {:cider/nrepl {:main-opts [\"-m\" \"nrepl.cmdline\" \"--middleware\" \"[cider.nrepl/cider-middleware]\"]}}}"
            "-M:foo-b:bar:cider/nrepl"]
           (repl-command/project->repl-start-command :clojure '("foo-b" "bar") [])))
    (is (= ["clojure"
                "-Sdeps"
                "{:deps {nrepl/nrepl {:mvn/version \"1.3.1\"} cider/cider-nrepl {:mvn/version \"0.55.7\"}} :aliases {:cider/nrepl {:main-opts [\"-m\" \"nrepl.cmdline\" \"--middleware\" \"[cider.nrepl/cider-middleware]\"]}}}"
                "-M:foo-b:bar:cider/nrepl"]
               (repl-command/project->repl-start-command :clojure '("foo-b" ":bar") [])))
    (testing "windows powershell"
      (with-redefs [repl-command/windows-os? (constantly true)
                    repl-command/locate-executable (fn [cmd]
                                                     (when (= "powershell" cmd)
                                                       "/full/path/powershell"))
                    repl-command/shell (constantly {:exit 0})]
        (is (= ["/full/path/powershell" "-NoProfile" "-Command" "clojure"
                "-Sdeps"
                "{:deps {nrepl/nrepl {:mvn/version \"1.3.1\"} cider/cider-nrepl {:mvn/version \"0.55.7\"}} :aliases {:cider/nrepl {:main-opts [\"-m\" \"nrepl.cmdline\" \"--middleware\" \"[cider.nrepl/cider-middleware]\"]}}}"
                "-M:cider/nrepl"]
               (repl-command/project->repl-start-command :clojure [] [])))))
    (testing "windows pwsh"
      (with-redefs [repl-command/windows-os? (constantly true)
                    repl-command/locate-executable (fn [cmd]
                                                     (when (= "pwsh" cmd)
                                                       "/full/path/pwsh"))
                    repl-command/shell (constantly {:exit 0})]
        (is (= ["/full/path/pwsh" "-NoProfile" "-Command" "clojure"
                "-Sdeps"
                "{:deps {nrepl/nrepl {:mvn/version \"1.3.1\"} cider/cider-nrepl {:mvn/version \"0.55.7\"}} :aliases {:cider/nrepl {:main-opts [\"-m\" \"nrepl.cmdline\" \"--middleware\" \"[cider.nrepl/cider-middleware]\"]}}}"
                "-M:cider/nrepl"]
               (repl-command/project->repl-start-command :clojure [] []))))))
  (testing "lein"
    (is (= ["lein" "update-in" ":dependencies" "conj" "[nrepl/nrepl \"1.3.1\"]"
            "--" "update-in" ":plugins" "conj" "[cider/cider-nrepl \"0.55.7\"]"
            "--" "repl" ":headless" ":host" "localhost"]
           (repl-command/project->repl-start-command :lein [] [])))
    (is (= ["lein" "update-in" ":dependencies" "conj" "[nrepl/nrepl \"1.3.1\"]"
            "--" "update-in" ":plugins" "conj" "[cider/cider-nrepl \"0.55.7\"]"
            "--" "with-profile" "+foo-b,+bar" "repl" ":headless" ":host" "localhost"]
           (repl-command/project->repl-start-command :lein ["foo-b" "bar"] [])))
    (testing "windows powershell"
      (with-redefs [repl-command/windows-os? (constantly true)
                    repl-command/locate-executable (fn [cmd]
                                                     (when (= "powershell" cmd)
                                                       "/full/path/powershell"))
                    repl-command/shell (constantly {:exit 0})]
        (is (= ["/full/path/powershell" "-NoProfile" "-Command" "lein.bat"
                "update-in" ":dependencies" "conj" "[nrepl/nrepl \"1.3.1\"]"
                "--" "update-in" ":plugins" "conj" "[cider/cider-nrepl \"0.55.7\"]"
                "--" "repl" ":headless" ":host" "localhost"]
               (repl-command/project->repl-start-command :lein [] [])))))
    (testing "windows pwsh"
      (with-redefs [repl-command/windows-os? (constantly true)
                    repl-command/locate-executable (fn [cmd]
                                                     (when (= "pwsh" cmd)
                                                       "/full/path/pwsh"))
                    repl-command/shell (constantly {:exit 0})]
        (is (= ["/full/path/pwsh" "-NoProfile" "-Command" "lein.bat"
                "update-in" ":dependencies" "conj" "[nrepl/nrepl \"1.3.1\"]"
                "--" "update-in" ":plugins" "conj" "[cider/cider-nrepl \"0.55.7\"]"
                "--" "repl" ":headless" ":host" "localhost"]
               (repl-command/project->repl-start-command :lein [] []))))))
  (testing "babashka"
    (is (= ["bb" "nrepl-server" "localhost:0"]
           (repl-command/project->repl-start-command :babashka [] []))))
  (testing "shadow-cljs"
    (is (= ["npx" "shadow-cljs" "server"]
           (repl-command/project->repl-start-command :shadow-cljs [] []))))
  (testing "boot"
    (is (= ["boot" "repl" "-s" "-b" "localhost" "wait"]
           (repl-command/project->repl-start-command :boot [] []))))
  (testing "nbb"
    (is (= ["nbb" "nrepl-server"]
           (repl-command/project->repl-start-command :nbb [] []))))
  (testing "gradle"
     (with-redefs [repl-command/windows-os? false]
     (is (= ["./gradlew"
            "-Pdev.clojurephant.jack-in.nrepl=nrepl:nrepl:1.3.1,cider:cider-nrepl:0.55.7"
            "clojureRepl"
            "--middleware=cider.nrepl/cider-middleware"]
           (repl-command/project->repl-start-command :gradle [] []))))
     (with-redefs [repl-command/windows-os? true]
         (is (= ["cmd" "/c" "gradlew.bat"
                "-Pdev.clojurephant.jack-in.nrepl=nrepl:nrepl:1.3.1,cider:cider-nrepl:0.55.7"
                "clojureRepl"
                "--middleware=cider.nrepl/cider-middleware"]
               (repl-command/project->repl-start-command :gradle [] []))))))
