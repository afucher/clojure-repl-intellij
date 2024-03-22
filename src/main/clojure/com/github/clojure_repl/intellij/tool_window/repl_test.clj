(ns com.github.clojure-repl.intellij.tool-window.repl-test
  (:gen-class
   :name com.github.clojure_repl.intellij.tool_window.ReplTestToolWindow
   :implements [com.intellij.openapi.wm.ToolWindowFactory
                com.intellij.openapi.project.DumbAware])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.ui.color :as ui.color]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.intellij.openapi.editor EditorFactory]
   [com.intellij.openapi.editor.impl EditorImpl]
   [com.intellij.openapi.fileTypes FileTypeManager]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.openapi.wm ToolWindow]
   [com.intellij.ui EditorTextField]
   [com.intellij.ui.content ContentFactory$SERVICE]
   [com.intellij.util.ui JBFont]
   [java.io File]
   [javax.swing JComponent JScrollPane]))

(set! *warn-on-reflection* true)

(defn ^:private any-clj-files? [dir]
  (->> (io/file dir)
       (walk/postwalk
        (fn [^File f]
          (if (.isDirectory f)
            (file-seq f)
            [f])))
       (some #(and (.isFile ^File %) (.endsWith (str %) ".clj")))
       boolean))

(def ^:private test-result-type->color
  {:pass ui.color/success-foreground
   :fail ui.color/fail-foreground
   :error ui.color/error-foreground})

(defn ^:private label [key value]
  (let [code ^String value
        document (.createDocument (EditorFactory/getInstance) code)
        clojure-file-type (.getStdFileType (FileTypeManager/getInstance) "clojure")
        any-project (first (.getOpenProjects (ProjectManager/getInstance)))]
    [[(seesaw/label :text key :foreground ui.color/low-light-foreground) "alignx right"]
     [(let [field (EditorTextField. document any-project clojure-file-type true true)]
        ;; We remove the border after the editor is built
        (app-manager/invoke-later! {:invoke-fn
                                    (fn []
                                      (.setBorder ^JScrollPane (.getScrollPane ^EditorImpl (.getEditor field)) nil))})
        field) "span"]]))

(defn ^:private test-report-content ^JComponent [vars]
  (mig/mig-panel
   :items
   (remove
    nil?
    (for [[_var tests] vars]
      (let [non-passing (remove #(= "pass" (:type %)) tests)]
        (when (seq non-passing)
          [(mig/mig-panel
            :items
            (concat [[(seesaw/flow-panel
                       :hgap 0
                       :vgap 0
                       :items [(seesaw/label :text (count non-passing) :font (.deriveFont (JBFont/label) java.awt.Font/BOLD))
                               (seesaw/label :text " non-passing tests:")]) "span"]]
                    (for [{:keys [var context type message expected actual diffs error gen-input]} non-passing]
                      [(mig/mig-panel
                        :items
                        (->> [[(seesaw/flow-panel
                                :vgap 0
                                :hgap 0
                                :items
                                [(seesaw/label :text (string/capitalize type)
                                               :foreground (test-result-type->color (keyword type)))
                                 (seesaw/label :text " in ")
                                 (seesaw/label :text var
                                               :foreground ui.color/info-foreground)]) "span"]
                              (when (seq context) [(seesaw/label :text (str context)) "span"])
                              (when (seq message) [(seesaw/label :text (str message)) "span"])
                              (when (seq expected)
                                (label "expected: " expected))
                              (if (seq diffs)
                                (for [[actual [removed added]] diffs]
                                  [(label "actual: " actual)
                                   (label "diff: " (str "- " removed))
                                   (label "" (str "+ " added))])

                                (when (seq actual)
                                  (label "actual: " actual)))
                              (when (seq error)
                                (label "" (str "error: " error)))
                              (when (seq gen-input)
                                (label "" (str "input: " gen-input)))]
                             flatten
                             (remove nil?)
                             (partition 2)
                             vec)) "span"]))) "span"]))))))

(defn ^:private on-test-failed [^ToolWindow tool-window {:keys [results]}]
  (let [content-factory (ContentFactory$SERVICE/getInstance)
        content-manager (.getContentManager tool-window)]
    (.removeAllContents content-manager false)
    (doseq [[ns vars] results]
      (.addContent content-manager
                   (.createContent content-factory
                                   (test-report-content vars)
                                   (name ns)
                                   false))))
  (.setAvailable tool-window true)
  (.show tool-window))

(defn ^:private on-test-succeeded [^ToolWindow tool-window _]
  (.removeAllContents (.getContentManager tool-window) false)
  (.setAvailable tool-window false)
  (.hide tool-window))

(defn -init [_ ^ToolWindow tool-window]
  (swap! db/db* update :on-test-failed-fns #(conj % (partial #'on-test-failed tool-window)))
  (swap! db/db* update :on-test-succeeded-fns #(conj % (partial #'on-test-succeeded tool-window))))

(defn -manager [_ _ _])

(defn -isApplicableAsync [_ ^Project project]
  (any-clj-files? (.getBasePath project)))

(def -isApplicable -isApplicableAsync)

(defn -shouldBeAvailable [_ _] false)

(defn -createToolWindowContent [_ _ _])
