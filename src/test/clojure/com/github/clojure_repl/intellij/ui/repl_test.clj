(ns com.github.clojure-repl.intellij.ui.repl-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is]]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]))

(defn code [& strings]
  (string/join "\n" strings))

(deftest extract-code-to-eval-test
  (is (= ["user> " "(+ 2 3)"]
         (#'ui.repl/extract-input+code-to-eval "user"
                                               (code ";; some text here"
                                                     "user> (+ 1 2)"
                                                     ";; => 1"
                                                     "user> (+ 2 3)"))))
  (is (= ["user> " "(tap> 1)"]
         (#'ui.repl/extract-input+code-to-eval "user"
                                               (code ";; some text here"
                                                     "user> 1"
                                                     ";; => 1"
                                                     "user> (tap> 1)"))))
  (is (= ["user-bar.baz> " "(tap> 1)"]
         (#'ui.repl/extract-input+code-to-eval "user-bar.baz"
                                               (code ";; some text here"
                                                     "user-bar.baz> 1"
                                                     ";; => 1"
                                                     "user-bar.baz> (tap> 1)"))))
  (is (= ["user-bar.baz> " (code "(defn foo []"
                                 "                123)")]
         (#'ui.repl/extract-input+code-to-eval "user-bar.baz"
                                               (code ";; some text here"
                                                     "user-bar.baz> 1"
                                                     ";; => 1"
                                                     "user-bar.baz> (defn foo []"
                                                     "                123)"))))
  (is (= ["user> " (code "(defn foo []"
                         "        (tap> 123)"
                         "        123)")]
         (#'ui.repl/extract-input+code-to-eval "user"
                                               (code ";; some text here"
                                                     "user> 1"
                                                     ";; => 1"
                                                     "user> (defn foo []"
                                                     "        (tap> 123)"
                                                     "        123)"))))
  (is (= ["user-bar.baz> " (code "(defn foo []"
                                 "                (tap> 123)"
                                 "                123)")]
         (#'ui.repl/extract-input+code-to-eval "user-bar.baz"
                                               (code ";; some text here"
                                                     "user-bar.baz> 1"
                                                     ";; => 1"
                                                     "user-bar.baz> (defn foo []"
                                                     "                (tap> 123)"
                                                     "                123)"))))
  (is (= ["user-bar.baz> " "(+ 2 3)"]
         (#'ui.repl/extract-input+code-to-eval "user-bar.baz"
                                               (code ";; some text here"
                                                     "user-bar.baz> "
                                                     "user-bar.baz> (+ 2 3)"))))
  (is (= ["user> " "(+ 2 3)"]
         (#'ui.repl/extract-input+code-to-eval "user"
                                               (code ";; some text here"
                                                     "user> 1"
                                                     "=> 1"
                                                     "user> 1")))))

(comment
  (extract-code-to-eval-test))
