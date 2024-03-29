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
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.color :as ui.color]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.util :as util]
   [com.rpl.proxy-plus :refer [proxy+]]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.intellij.openapi.editor Editor EditorFactory]
   [com.intellij.openapi.editor.impl EditorImpl]
   [com.intellij.openapi.fileEditor FileDocumentManager FileEditorManager]
   [com.intellij.openapi.fileTypes FileTypeManager]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.openapi.util.text StringUtil]
   [com.intellij.openapi.wm ToolWindow]
   [com.intellij.ui EditorTextField]
   [com.intellij.ui.components ActionLink]
   [com.intellij.ui.content ContentFactory$SERVICE]
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
  {:pass ui.color/normal-foreground
   :fail ui.color/fail-foreground
   :error ui.color/error-foreground})

(defn ^:private label [key value]
  ;; TODO support ANSI colors for libs like matcher-combinators pretty prints.
  (let [code ^String (string/replace value #"\u001B\[[;\d]*m" "")
        document (.createDocument (EditorFactory/getInstance) code)
        clojure-file-type (.getStdFileType (FileTypeManager/getInstance) "clojure")
        any-project (first (.getOpenProjects (ProjectManager/getInstance)))]
    [[(seesaw/label :text key :foreground ui.color/low-light-foreground) "alignx right, aligny top"]
     [(let [field (EditorTextField. document any-project clojure-file-type true false)]
        ;; We remove the border after the editor is built
        (app-manager/invoke-later! {:invoke-fn
                                    (fn []
                                      (.setBorder ^JScrollPane (.getScrollPane ^EditorImpl (.getEditor field)) nil))})
        field) "span"]]))

(defn ^:private navigate-to-test [{:keys [ns var]}]
  (let [{:keys [file line column]} (nrepl/sym-info ns var)
        project (first (.getOpenProjects (ProjectManager/getInstance)))
        editor ^Editor (util/uri->editor file project true)
        offset (StringUtil/lineColToOffset (.getText (.getDocument editor)) (dec line) (dec column))
        file-editor-manager (FileEditorManager/getInstance project)
        file-document-manager (FileDocumentManager/getInstance)
        v-file (.getFile file-document-manager (.getDocument editor))]
    (.openFile file-editor-manager v-file true)
    (.moveToOffset (.getCaretModel editor) offset)))

(defn ^:private test-report-content ^JComponent [vars]
  (seesaw/scrollable
   (mig/mig-panel
    :items
    (remove
     nil?
     (for [[_var tests] vars]
       (let [non-passing (remove #(= "pass" (:type %)) tests)]
         (when (seq non-passing)
           [(mig/mig-panel
             :items
             (for [{:keys [var context type message expected actual diffs error gen-input] :as test} non-passing]
               [(mig/mig-panel
                 :items
                 (->> [[(seesaw/flow-panel
                         :vgap 0
                         :hgap 0
                         :items
                         [(seesaw/label :text (string/capitalize type)
                                        :foreground (test-result-type->color (keyword type)))
                          (seesaw/label :text " in ")
                          (ActionLink. ^String var (proxy+ [] java.awt.event.ActionListener
                                                     (actionPerformed [_ _] (navigate-to-test test))))]) "span"]
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
                         [(label "error: " error)
                               ;; TODO implement support to check stacktrace error
                          #_[(seesaw/button :text "View stacktrace"
                                            :mnemonic "S"
                                            :listen [:action (fn [_]
                                                               (seesaw/alert "fooo"
                                                                             :title "Test error stacktrace"
                                                                             :type :error
                                                                             :icon Icons/CLOJURE_REPL))]) ""]])
                       (when (seq gen-input)
                         (label "input: " gen-input))]
                      flatten
                      (remove nil?)
                      (partition 2)
                      vec)) "span"])) "span"])))))
   :border nil))

(defn ^:private test-report-title-summary [summary elapsed-time]
  (let [failed (when (> (:fail summary) 0) (:fail summary))
        error (when (> (:error summary) 0) (:error summary))
        ms (:ms elapsed-time)]
    (cond-> ""
      failed
      (str failed " failed")

      (and failed error)
      (str ", ")

      error
      (str error " errors")

      ms
      (str " in " ms "ms"))))

(defn ^:private on-test-failed [^ToolWindow tool-window {:keys [results summary elapsed-time]}]
  (let [content-factory (ContentFactory$SERVICE/getInstance)
        content-manager (.getContentManager tool-window)]
    (app-manager/invoke-later!
     {:invoke-fn (fn []
                   (.removeAllContents content-manager false)
                   (doseq [[_ vars] results]
                     (.addContent content-manager
                                  (.createContent content-factory
                                                  (test-report-content vars)
                                                  (test-report-title-summary summary elapsed-time)
                                                  false)))
                   (.setAvailable tool-window true)
                   (.show tool-window))})))

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
