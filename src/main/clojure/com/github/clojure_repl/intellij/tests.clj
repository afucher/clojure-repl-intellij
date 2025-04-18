(ns com.github.clojure-repl.intellij.tests
  (:require
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
  (let [project (.getProject editor)]
    (if-not (db/get-in project [:current-nrepl :session-id])
      (ui.hint/show-error :message "No REPL connected" :editor editor)
      ;; TODO save last result and summary
      (tasks/run-background-task!
       project
       "REPL: Running test"
       (fn [_indicator]
         (db/assoc-in! project [:current-nrepl :last-test] {:ns ns :tests tests})
         (nrepl/run-tests
          project
          {:ns ns
           :tests tests
           :on-ns-not-found (fn [ns]
                              (app-manager/invoke-later!
                               {:invoke-fn
                                (fn []
                                  (ui.hint/show-error :message (format "No namespace '%s' found. Did you load the file?" ns) :editor editor))}))
           :on-out (fn [out]
                     (ui.repl/append-output project out))
           :on-err (fn [err]
                     (ui.repl/append-output project err))
           :on-failed (fn [result]
                        ;; TODO highlight errors on editor
                        (doseq [[key fns] (db/get-in project [:on-test-failed-fns-by-key])]
                          (doseq [fn fns]
                            (fn project key result))))
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
                               (doseq [[key fns] (db/get-in project [:on-test-succeeded-fns-by-key])]
                                 (doseq [fn fns]
                                   (fn project key response))))}))}))))))

(defn run-at-cursor [^Editor editor]
  (let [text (.getText (.getDocument editor))
        root-zloc (z/of-string text)
        [row col] (util/editor->cursor-position editor)
        ns (-> (parser/find-namespace root-zloc) z/string parser/remove-metadata)]
    (if-let [test (some-> (parser/find-var-at-pos root-zloc (inc row) col) z/string)]
      (run editor ns [test])
      (ui.hint/show-error :message "No test var found, did you eval the var?" :editor editor))))

(defn run-ns-tests [^Editor editor]
  (let [text (.getText (.getDocument editor))
        root-zloc (z/of-string text)
        zloc (parser/find-namespace root-zloc)
        ns (parser/remove-metadata (z/string zloc))]
    (run editor ns nil)))

(defn re-run-test [^Editor editor]
  (if-let [{:keys [ns tests]} (db/get-in (.getProject editor) [:current-nrepl :last-test])]
    (run editor ns tests)
    (ui.hint/show-error :message "No last test found" :editor editor)))
