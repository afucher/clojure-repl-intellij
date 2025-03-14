(ns com.github.clojure-repl.intellij.db
  (:refer-clojure :exclude [get-in assoc-in update-in])
  (:require
   [com.github.clojure-repl.intellij.db :as db])
  (:import
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* true)

(def ^:private empty-project
  {:project nil
   :console {:state {:last-output ""
                     :last-input nil
                     :initial-text nil
                     :status nil}
             :process-handler nil
             :ui nil}
   :current-nrepl {:session-id nil
                   :ns "user"
                   :nrepl-port nil
                   :nrepl-host nil
                   :entry-history '()
                   :entry-index -1
                   :last-test nil}
   :file->ns {}
   :on-repl-file-loaded-fns []
   :on-repl-evaluated-fns []
   :on-ns-changed-fns []
   :on-test-failed-fns-by-key {}
   :on-test-succeeded-fns-by-key {}
   :ops {}})

(defonce ^:private db* (atom {:projects {}}))

(defn get-in
  ([project fields]
   (get-in project fields nil))
  ([^Project project fields default]
   (clojure.core/get-in @db* (concat [:projects (.getBasePath project)] fields) default)))

(defn assoc-in! [^Project project fields value]
  (swap! db* clojure.core/assoc-in (concat [:projects (.getBasePath project)] fields) value))

(defn update-in! [^Project project fields fn]
  (swap! db* clojure.core/update-in (concat [:projects (.getBasePath project)] fields) fn))

(defn init-db-for-project [^Project project]
  (swap! db* update :projects (fn [projects]
                                (if (clojure.core/get-in projects [(.getBasePath project) :project])
                                  projects
                                  (update projects (.getBasePath project) #(merge (assoc empty-project :project project) %))))))

(defn all-projects []
  (remove nil?
          (mapv :project (vals (:projects @db*)))))

(comment
  ;; Useful for db debugging
  (require '[clojure.pprint :as pp])
  (defn db-p [] (second (first  (second (first @db*)))))
  (def p (:project (db-p)))

  (:state (:console (db-p)))
  (:ns (:current-nrepl (db-p)))

  (assoc-in! p [:ops] {})
  (assoc-in! p [:on-ns-changed-fns] [])
  (pp/pprint (:ops (db-p))))
