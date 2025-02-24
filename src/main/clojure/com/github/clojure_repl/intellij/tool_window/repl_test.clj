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
   [com.github.clojure-repl.intellij.ui.components :as ui.components]
   [com.github.clojure-repl.intellij.ui.font :as ui.font]
   [com.github.clojure-repl.intellij.ui.text :as ui.text]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.github.ericdallo.clj4intellij.util :as util]
   [com.rpl.proxy-plus :refer [proxy+]]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.github.clojure_repl.intellij Icons]
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.editor.impl EditorImpl]
   [com.intellij.openapi.fileEditor FileDocumentManager FileEditorManager]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.openapi.util.text StringUtil]
   [com.intellij.openapi.wm ToolWindow ToolWindowAnchor]
   [com.intellij.ui.components ActionLink]
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

(defn ^:private test-result-type->color [type]
  (get {:pass (.getForegroundColor (ui.color/test-result-pass))
        :fail (.getForegroundColor (ui.color/test-result-fail))
        :error (.getForegroundColor (ui.color/test-result-error))}
       type))

(defn ^:private label [key value]
  ;; TODO support ANSI colors for libs like matcher-combinators pretty prints.
  (let [code (ui.text/remove-ansi-color value)
        any-project (first (.getOpenProjects (ProjectManager/getInstance)))
        font (ui.font/code-font (ui.color/test-summary-code))]
    [[(seesaw/label :text key :font font :foreground (.getForegroundColor (ui.color/test-summary-label))) "alignx right, aligny top"]
     [(let [field (ui.components/clojure-text-field
                   :editable? false
                   :text code
                   :font font
                   :project any-project)]
        ;; We remove the border after the editor is built
        (app-manager/invoke-later! {:invoke-fn
                                    (fn []
                                      (.setBorder ^JScrollPane (.getScrollPane ^EditorImpl (.getEditor field)) nil))})
        field) "span"]]))

(defn ^:private navigate-to-test [project {:keys [ns var]}]
  (let [{:keys [file line column]} (nrepl/sym-info project ns var)
        project (first (.getOpenProjects (ProjectManager/getInstance)))
        editor ^Editor (util/uri->editor file project true)
        offset (StringUtil/lineColToOffset (.getText (.getDocument editor)) (dec line) (dec column))
        file-editor-manager (FileEditorManager/getInstance project)
        file-document-manager (FileDocumentManager/getInstance)
        v-file (.getFile file-document-manager (.getDocument editor))]
    (.openFile file-editor-manager v-file true)
    (.moveToOffset (.getCaretModel editor) offset)))

(defn ^:private view-error [project ns var]
  (ui.components/collapsible
   :expanded-title "Hide error"
   :collapsed-title "View error"
   :title-font (.asBold (JBFont/h3))
   :content (fn []
              (let [{:keys [class stacktrace message data]} (nrepl/test-stacktrace project ns var)
                    code-font (ui.font/code-font (ui.color/test-summary-code))
                    [max-file-length
                     max-line-length] (reduce (fn [[max-f-size max-l-size] {:keys [file line]}]
                                                (let [c-lines (count (str line))
                                                      c-file (count file)
                                                      file-l (if (> c-file max-f-size) c-file max-f-size)
                                                      line-l (if (> c-lines max-l-size) c-lines max-l-size)]
                                                  [file-l line-l])) [0 0] stacktrace)
                    stacktrace-text (reduce (fn [text {:keys [fn ns name file line]}]
                                              (format (str "%s%" max-file-length "s %" max-line-length "s %s\n")
                                                      text
                                                      file
                                                      line
                                                      (if fn (str ns "/" fn) name))) "" stacktrace)]
                (mig/mig-panel
                 :constraints ["insets 0, gap 8"]
                 :items [[(seesaw/horizontal-panel
                           :items [(seesaw/label :text "Exception: " :font (JBFont/regular))
                                   (seesaw/label :text class
                                                 :foreground (.getForegroundColor (ui.color/test-result-error))
                                                 :font code-font)]) "span"]
                         [(ui.components/clojure-text-field :text (ui.text/remove-ansi-color message)
                                                            :project project
                                                            :font code-font
                                                            :editable? false) "span"]
                         [(ui.components/clojure-text-field :text (ui.text/pretty-printed-clojure-text data)
                                                            :project project
                                                            :font code-font
                                                            :editable? false) "span"]
                         ;; TODO improve this component to support clickable links for file-uri
                         [(ui.components/clojure-text-field :text stacktrace-text
                                                            :project project
                                                            :font code-font
                                                            :editable? false) "span"]])))))

(defn ^:private test-report-content ^JComponent [^Project project vars]
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
             (for [{:keys [ns var context type message expected actual diffs error gen-input] :as test} non-passing]
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
                                                     (actionPerformed [_ _] (navigate-to-test project test))))]) "span"]
                       (when (seq context) [(seesaw/label :text (str context)) "span"])
                       (when (seq message) [(seesaw/label :text (str message)) "span"])
                       (when (seq expected)
                         (label "expected:" expected))
                       (if (seq diffs)
                         (for [[actual [removed added]] diffs]
                           [(label "actual:" actual)
                            (label "diff:" (str "- " removed))
                            (label "" (str "+ " added))])

                         (when (seq actual)
                           (label "actual:" actual)))
                       (when (seq error)
                         [(label "error:" error)
                          [(view-error project ns var) "span"]])
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

(defn ^:private on-test-failed [^Project project ^ToolWindow tool-window {:keys [results summary elapsed-time]}]
  (when-not (.isDisposed tool-window)
    (let [content-factory (ContentFactory$SERVICE/getInstance)
          content-manager (.getContentManager tool-window)]
      (app-manager/invoke-later!
       {:invoke-fn (fn []
                     (.removeAllContents content-manager false)
                     (doseq [[_ vars] results]
                       (.addContent content-manager
                                    (.createContent content-factory
                                                    (test-report-content project vars)
                                                    (test-report-title-summary summary elapsed-time)
                                                    false)))
                     (.setAvailable tool-window true)
                     (.show tool-window))}))))

(defn ^:private on-test-succeeded [^Project _project ^ToolWindow tool-window _]
  (when-not (.isDisposed tool-window)
    (.removeAllContents (.getContentManager tool-window) false)
    (.setAvailable tool-window false)
    (.hide tool-window)))

(set! *warn-on-reflection* false)
(defn -init [_ ^ToolWindow tool-window]
  (let [project (.getProject tool-window)]
    (db/update-in! project [:on-test-failed-fns-by-key tool-window] #(conj % #'on-test-failed))
    (db/update-in! project [:on-test-succeeded-fns-by-key tool-window] #(conj % #'on-test-succeeded))))
(set! *warn-on-reflection* true)

(defn -manager [_ _ _])

(defn -isApplicableAsync
  ([_ ^Project project]
   (any-clj-files? (.getBasePath project)))
  ([_ ^Project project _]
   (-isApplicableAsync _ project)))

(def -isApplicable -isApplicableAsync)

(defn -shouldBeAvailable [_ _] false)

(defn -createToolWindowContent [_ _ _])

(defn -getIcon [_] Icons/CLOJURE_REPL)

(defn -getAnchor [_] ToolWindowAnchor/BOTTOM)

(defn -manage [_ _ _ _])
