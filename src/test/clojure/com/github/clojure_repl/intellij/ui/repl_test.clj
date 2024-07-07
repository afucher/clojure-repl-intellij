(ns com.github.clojure-repl.intellij.ui.repl-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is]]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]))

(defn code [& strings]
  (string/join "\n" strings))

(deftest extract-code-to-eval-test
  (is (= "(+ 2 3)"
         (#'ui.repl/extract-code-to-eval (code ";; some text here"
                                               "user> (+ 1 2)"
                                               ";; => 1"
                                               "user> (+ 2 3)"))))
  (is (= "(tap> 1)"
         (#'ui.repl/extract-code-to-eval (code ";; some text here"
                                               "user> 1"
                                               ";; => 1"
                                               "user> (tap> 1)"))))
  (is (= "(tap> 1)"
         (#'ui.repl/extract-code-to-eval (code ";; some text here"
                                               "user-bar.baz> 1"
                                               ";; => 1"
                                               "user-bar.baz> (tap> 1)"))))
  (is (= (code "(defn foo []"
               "                123)")
         (#'ui.repl/extract-code-to-eval (code ";; some text here"
                                               "user-bar.baz> 1"
                                               ";; => 1"
                                               "user-bar.baz> (defn foo []"
                                               "                123)"))))
  (is (= (code  "(defn foo []"
                "        (tap> 123)"
                "        123)")
         (#'ui.repl/extract-code-to-eval (code ";; some text here"
                                               "user> 1"
                                               ";; => 1"
                                               "user> (defn foo []"
                                               "        (tap> 123)"
                                               "        123)"))))
  (is (= (code  "(defn foo []"
                "                (tap> 123)"
                "                123)")
         (#'ui.repl/extract-code-to-eval (code ";; some text here"
                                               "user-bar.baz> 1"
                                               ";; => 1"
                                               "user-bar.baz> (defn foo []"
                                               "                (tap> 123)"
                                               "                123)")))))

(comment
  (extract-code-to-eval-test))
