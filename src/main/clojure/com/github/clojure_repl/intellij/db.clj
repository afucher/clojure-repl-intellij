(ns com.github.clojure-repl.intellij.db
  (:refer-clojure :exclude [get-in assoc-in update-in])
  (:require
   [com.github.clojure-repl.intellij.db :as db])
  (:import
   [com.intellij.openapi.project Project]))

(set! *warn-on-reflection* true)

(def ^:private empty-project
  {:project nil
   :console {:state {:last-output nil
                     :initial-text nil
                     :status nil}
             :process-handler nil
             :ui nil}
   :run-configuration {:ui nil}
   :current-nrepl {:session-id nil
                   :ns "user"}
   :settings {:nrepl-port nil
              :nrepl-host "localhost"
              :remote-repl-mode :manual-config}
   :on-repl-file-loaded-fns []
   :on-repl-evaluated-fns []
   :ops {}})

(defonce ^:private db* (atom {:projects {}
                              :on-test-failed-fns-by-key {}
                              :on-test-succeeded-fns-by-key {}}))

(defn get-in
  ([project fields]
   (get-in project fields nil))
  ([^Project project fields default]
   (clojure.core/get-in @db* (concat [:projects (.getBasePath project)] fields) default)))

(defn global-get-in
  ([fields]
   (global-get-in fields nil))
  ([fields default]
   (clojure.core/get-in @db* fields default)))

(defn assoc-in [^Project project fields value]
  (swap! db* clojure.core/assoc-in (concat [:projects (.getBasePath project)] fields) value))

(defn update-in [^Project project fields fn]
  (swap! db* clojure.core/update-in (concat [:projects (.getBasePath project)] fields) fn))

(defn global-update-in [fields value]
  (swap! db* clojure.core/update-in fields value))

(defn init-db-for-project [^Project project]
  (swap! db* update :projects (fn [projects]
                                (if (clojure.core/get projects (.getBasePath project))
                                  projects
                                  (assoc projects (.getBasePath project) (assoc empty-project :project project))))))
