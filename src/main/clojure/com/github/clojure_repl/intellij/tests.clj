(ns com.github.clojure-repl.intellij.tests
  (:require
   [com.github.clojure-repl.intellij.configuration.factory.base :as config.factory.base]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.clojure-repl.intellij.ui.hint :as ui.hint]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.tasks :as tasks]
   [com.github.ericdallo.clj4intellij.util :as util]
   [rewrite-clj.zip :as z])
  (:import
   [com.intellij.openapi.editor Editor]))

(set! *warn-on-reflection* true)

(defn ^:private run [^Editor editor ns tests]
  (if-not (-> @db/db* :current-nrepl :session-id)
    (ui.hint/show-error :message "No REPL connected" :editor editor)
    ;; TODO save last result and summary
    (tasks/run-background-task!
     (.getProject editor)
     "REPL: Running test"
     (fn [_indicator]
       (nrepl/run-tests
        {:ns ns
         :tests tests
         :on-ns-not-found (fn [ns]
                            (app-manager/invoke-later!
                             {:invoke-fn
                              (fn []
                                (ui.hint/show-error :message (format "No namespace '%s' found" ns) :editor editor))}))
         :on-out (fn [out]
                   (ui.repl/append-result-text (:console @config.factory.base/current-repl*) out))
         :on-err (fn [err]
                   (ui.repl/append-result-text (:console @config.factory.base/current-repl*) err))
         :on-failed (fn [result]
                        ;; TODO highlight errors on editor
                      (doseq [fns (:on-test-failed-fns @db/db*)]
                        (fns result)))
         :on-succeeded (fn [{{:keys [ns test var fail error]} :summary results :results elapsed-time :elapsed-time :as response}]
                         (app-manager/invoke-later!
                          {:invoke-fn
                           (fn []
                             (if (empty? results)
                               (ui.hint/show-info :message "No assertions (or no tests) were run. Did you forget to use `is' in your tests?\n" :editor editor)
                               (ui.hint/show-success :message (format "%s: Ran %s assertions, in %s test functions. %s failures, %s errors%s\n"
                                                                      (if (= 1 ns)
                                                                        (name (ffirst results))
                                                                        (str ns " namespaces"))
                                                                      test
                                                                      var
                                                                      fail
                                                                      error
                                                                      (or (some->> (:ms elapsed-time) (format " in %s ms"))
                                                                          ".")) :editor editor))
                             (doseq [fns (:on-test-succeeded-fns @db/db*)]
                               (fns response)))}))})))))

(defn run-at-cursor [^Editor editor]
  (let [text (.getText (.getDocument editor))
        root-zloc (z/of-string text)
        [row col] (util/editor->cursor-position editor)
        ns (-> (parser/find-namespace root-zloc) z/string)]
    (if-let [test (some-> (parser/find-var-at-pos root-zloc (inc row) col) z/string)]
      (run editor ns [test])
      (ui.hint/show-error :message "No test var found" :editor editor))))

(defn run-ns-tests [^Editor editor]
  (let [text (.getText (.getDocument editor))
        root-zloc (z/of-string text)
        zloc (parser/find-namespace root-zloc)
        ns (z/string zloc)]
    (run editor ns nil)))
