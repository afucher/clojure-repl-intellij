(ns com.github.clojure-repl.intellij.tests
  (:require
   [com.github.clojure-repl.intellij.configuration.factory.base :as config.factory.base]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.hint :as ui.hint]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl])
  (:import
   [com.intellij.openapi.editor Editor]))

(defn run [& {:keys [ns ^Editor editor]}]
  (nrepl/run-tests
   ns
   {:on-ns-not-found (fn [ns]
                       (ui.hint/show-error :message (format "No namespace '%s' found" ns) :editor editor))
    :on-out (fn [out]
              (ui.repl/append-result-text (:console @config.factory.base/current-repl*) out))
    :on-err (fn [err]
              (ui.repl/append-result-text (:console @config.factory.base/current-repl*) err))
    :on-failed (fn [result]
                 (doseq [fns (:on-test-failed-fns @db/db*)]
                   (fns result)))
    :on-succeeded (fn [{{:keys [ns test var fail error]} :summary results :results elapsed-time :elapsed-time :as response}]
                    (let [message (if (empty? results)
                                    "No assertions (or no tests) were run. Did you forget to use `is' in your tests?\n"
                                    (format "%s: Ran %s assertions, in %s test functions. %s failures, %s errors%s\n"
                                            (if (= 1 ns)
                                              (name (ffirst results))
                                              (str ns " namespaces"))
                                            test
                                            var
                                            fail
                                            error
                                            (or (some->> (:ms elapsed-time) (format " in %s ms"))
                                                ".")))]
                      (ui.hint/show-success :message message :editor editor)
                      (doseq [fns (:on-test-succeeded-fns @db/db*)]
                        (fns response))))}))
