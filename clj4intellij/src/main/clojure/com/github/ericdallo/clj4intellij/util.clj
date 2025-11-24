(ns com.github.ericdallo.clj4intellij.util
  (:require
   [clojure.java.io :as io])
  (:import
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.fileEditor FileEditorManager OpenFileDescriptor TextEditor]
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.util.text StringUtil]
   [com.intellij.openapi.vfs LocalFileSystem VirtualFile]))

(defn editor->cursor-position [^Editor editor]
  (let [offset (.. editor getCaretModel getCurrentCaret getOffset)
        text (.getCharsSequence (.getDocument editor))
        line-col (StringUtil/offsetToLineColumn text offset)]
    [(.line line-col) (.column line-col)]))

(defn v-file->editor ^Editor [^VirtualFile v-file ^Project project ^Boolean focus]
  (let [file-manager (FileEditorManager/getInstance project)
        editor (if (.isFileOpen file-manager v-file)
                 (.getEditor ^TextEditor (first (.getAllEditors file-manager v-file)))
                 (.openTextEditor file-manager (OpenFileDescriptor. project v-file) focus))]
    editor))

(defn uri->v-file ^VirtualFile [^String uri]
  (.findFileByIoFile (LocalFileSystem/getInstance)
                     (io/file (java.net.URI. uri))))

(defn uri->editor ^Editor [^String uri ^Project project ^Boolean focus?]
  (let [v-file (uri->v-file uri)]
    (v-file->editor v-file project focus?)))
