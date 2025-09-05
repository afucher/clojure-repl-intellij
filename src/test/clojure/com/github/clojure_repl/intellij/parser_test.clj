(ns com.github.clojure-repl.intellij.parser-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.github.clojure-repl.intellij.parser :as parser]
   [rewrite-clj.zip :as z]))

(deftest find-var-at-pos-test
  (testing "should find var name in deftest form"
    (let [zloc (z/of-string "(deftest a 1)")]
      (is (= "a" (-> (parser/find-var-at-pos zloc 1 4) z/string))
          "find-var-at-pos should return 'a' for (deftest a 1)")))

  (testing "should find var name in s/deftest form"
    (let [zloc (z/of-string "(s/deftest a 1)")]
      (is (= "a" (-> (parser/find-var-at-pos zloc 1 4) z/string))
          "find-var-at-pos should return 'a' for (s/deftest a 1)")))

  (testing "should handle other def-like forms"
    (let [defn-zloc (z/of-string "(defn my-function [] 1)")
          def-zloc (z/of-string "(def my-var 42)")]
      (is (= "my-function" (-> (parser/find-var-at-pos defn-zloc 1 4) z/string))
          "find-var-at-pos should return 'my-function' for (defn my-function [] 1)")
      (is (= "my-var" (-> (parser/find-var-at-pos def-zloc 1 4) z/string))
          "find-var-at-pos should return 'my-var' for (def my-var 42)")))

  (testing "should return nil for non-def forms"
    (let [zloc (z/of-string "(+ 1 2)")]
      (is (nil? (parser/find-var-at-pos zloc 1 2))
          "find-var-at-pos should return nil for non-def forms"))))
