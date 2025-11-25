(ns com.github.clojure-repl.intellij.extension.load-file-on-save-listener
  (:gen-class
   :name com.github.clojure_repl.intellij.extension.LoadFileOnSaveListener
   :implements [com.intellij.openapi.fileEditor.FileDocumentManagerListener])
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.github.ericdallo.clj4intellij.tasks :as tasks])
  (:import
   [com.github.clojure_repl.intellij ClojureReplSettings]
   [com.intellij.openapi.editor Document]
   [com.intellij.openapi.fileEditor FileDocumentManager]
   [com.intellij.openapi.project ProjectLocator]
   [com.intellij.openapi.vfs VirtualFile]))

(set! *warn-on-reflection* true)

(def ^:private clojure-extensions
  #{"clj" "cljc" "cljs"})

(defn ^:private clojure-file? [^VirtualFile virtual-file]
  (when virtual-file
    (contains? clojure-extensions (.getExtension virtual-file))))

(defn ^:private load-file-to-repl [project ^Document document ^VirtualFile virtual-file]
  (tasks/run-background-task!
   project
   "REPL: Loading file on save"
   (fn [_indicator]
     (try
       (let [response (nrepl/load-file-from-document project document virtual-file)
             msg (str ";; Loaded file on save: " (.getPath virtual-file))]
         (when-not (contains? (:status response) "eval-error")
           (ui.repl/append-output project (str "\n" msg))))
       (catch Exception e
         (logger/error "Error loading file on save:" (.getMessage e)))))))

(defn -beforeDocumentSaving [_this ^Document document]
  (when (.getLoadFileOnSave (ClojureReplSettings/getInstance))
    (when-let [virtual-file (.getFile (FileDocumentManager/getInstance) document)]
      (when (clojure-file? virtual-file)
        (when-let [project (.guessProjectForFile (ProjectLocator/getInstance) virtual-file)]
          (when (db/get-in project [:current-nrepl :session-id])
            (load-file-to-repl project document virtual-file)))))))

;; Required interface methods (all have default implementations in Java, but gen-class requires them)
(defn -beforeAllDocumentsSaving [_this])
(defn -beforeAnyDocumentSaving [_this _document _explicit])
(defn -beforeFileContentReload [_this _file _document])
(defn -fileWithNoDocumentChanged [_this _file])
(defn -fileContentReloaded [_this _file _document])
(defn -fileContentLoaded [_this _file _document])
(defn -unsavedDocumentDropped [_this _document])
(defn -unsavedDocumentsDropped [_this])
(defn -afterDocumentUnbound [_this _file _document])
