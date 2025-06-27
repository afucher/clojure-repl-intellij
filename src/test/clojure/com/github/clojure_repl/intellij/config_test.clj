(ns com.github.clojure-repl.intellij.config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.github.clojure-repl.intellij.config :refer [safe-read-edn-string]]))

(defn fn-error [& _] nil)

(deftest safe-read-edn-string-test
  (testing "no content"
    (is (= []
           (safe-read-edn-string "" fn-error)))
    (is (= []
           (safe-read-edn-string "{}" fn-error)))
    (is (= []
           (safe-read-edn-string "{:eval-code-actions []}" fn-error))))
  (testing "simple code-actions"
    (is (= [{:name "test"
             :code "(println \"test\")"}]
           (safe-read-edn-string "{:eval-code-actions [{:name \"test\" :code (println \"test\")}]}" fn-error)))
    (is (= [{:name "test"
             :code "(println \"test\")"}
            {:name "test 2"
             :code "(println \"test 2\")"}]
           (safe-read-edn-string "{:eval-code-actions [{:name \"test\" :code (println \"test\")}\n{:name \"test 2\" :code (println \"test 2\")}]}" fn-error))))
  (testing "code-action with reader macros"
    (is (= [{:name "test"
             :code "(println @my-var)"}]
           (safe-read-edn-string "{:eval-code-actions [{:name \"test\" :code (println @my-var)}]}" fn-error)))
    (is (= [{:name "test"
             :code "(do (println @my-var)\n#_(commented_fn args)\n(+ 1 1))"}]
           (safe-read-edn-string "{:eval-code-actions [{:name \"test\" :code (do (println @my-var)\n#_(commented_fn args)\n(+ 1 1))}]}" fn-error))))
  (testing "code-action with placeholder selectors"
    (is (= [{:name "test"
             :code "(println $selection)"}]
           (safe-read-edn-string "{:eval-code-actions [{:name \"test\" :code (println $selection)}]}" fn-error)))
    (is (= [{:name "test"
             :code "(println #'$current-var)"}]
           (safe-read-edn-string "{:eval-code-actions [{:name \"test\" :code (println #'$current-var)}]}" fn-error))))
  (testing "when error, calls error fn and return empty list"
    (let [error-fn-called? (atom false)
          error-fn (fn [& _] (reset! error-fn-called? true) nil)]
      (is (= []
             (safe-read-edn-string "{:eval-code-actions [{:name \"test\" :code (println \"test\")" error-fn)))
      (is (true? @error-fn-called?)))))

