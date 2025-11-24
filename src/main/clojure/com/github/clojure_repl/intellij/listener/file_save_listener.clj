(ns com.github.clojure-repl.intellij.listener.file-save-listener
  (:gen-class
   :name com.github.clojure_repl.intellij.listener.FileSaveListener
   :implements [com.intellij.openapi.fileEditor.FileDocumentManagerListener])
  (:require
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.nrepl :as nrepl]
   [com.github.clojure-repl.intellij.settings.state :as settings-state]
   [com.github.clojure-repl.intellij.ui.repl :as ui.repl]
   [com.github.ericdallo.clj4intellij.logger :as logger])
  (:import
   [com.intellij.openapi.application ApplicationManager]
   [com.intellij.openapi.document Document]
   [com.intellij.openapi.editor EditorFactory]
   [com.intellij.openapi.fileEditor FileDocumentManager]
   [com.intellij.openapi.project Project ProjectManager]
   [com.intellij.openapi.vfs VirtualFile]))

(set! *warn-on-reflection* true)

(defn ^:private is-clojure-file? [^VirtualFile file]
  (when file
    (let [extension (.getExtension file)]
      (or (= extension "clj")
          (= extension "cljs")
          (= extension "cljc")
          (= extension "edn")))))

(defn ^:private find-project-for-file [^VirtualFile file]
  (let [project-manager (ProjectManager/getInstance)
        open-projects (.getOpenProjects project-manager)]
    (some (fn [^Project project]
            (when (and (not (.isDisposed project))
                       (.getProjectFile project))
              (let [project-dir (.getParent (.getProjectFile project))]
                (when (and project-dir
                           (.isAncestor project-dir file false))
                  project))))
          open-projects)))

(defn ^:private load-file-to-repl [^Document document ^VirtualFile file]
  (try
    (when-let [project (find-project-for-file file)]
      (when (db/get-in project [:current-nrepl :session-id])
        (let [editors (.getEditors (EditorFactory/getInstance) document)
              editor (when (seq editors) (first editors))]
          (when editor
            (logger/info (str "Auto-loading file to REPL: " (.getPath file)))
            (let [response (nrepl/load-file project editor file)]
              (when-not (contains? (:status response) "error")
                (ui.repl/append-output
                 project
                 (str "\n;; Auto-loaded file " (.getPath file)))))))))
    (catch Exception e
      (logger/error "Error auto-loading file to REPL:" e))))

(defn -beforeDocumentSaving [_this ^Document document]
  (when (settings-state/auto-load-on-save-enabled?)
    (let [file-doc-manager (FileDocumentManager/getInstance)
          file (.getFile file-doc-manager document)]
      (when (and file (is-clojure-file? file))
        (.invokeLater (ApplicationManager/getApplication)
         (proxy [Runnable] []
           (run []
             (load-file-to-repl document file))))))))


(defn -init []
  [[] (atom {:auto-load-on-save false})])

(defn -getState [this]
  @(.state this))

(defn -loadState [this state]
  (reset! (.state this) state))

(defn get-instance []
  (com.intellij.openapi.application.ApplicationManager/getApplication
   (.getService com.github.clojure_repl.intellij.settings.PluginSettingsState)))

(defn auto-load-on-save-enabled? []
  (get (.-getState (get-instance)) :auto-load-on-save false))

(defn set-auto-load-on-save! [enabled?]
  (swap! (.state (get-instance)) assoc :auto-load-on-save enabled?))

