(ns com.github.ericdallo.clj4intellij.app-manager
  (:import
   [com.intellij.openapi.application ApplicationManager ModalityState]
   [com.intellij.openapi.command CommandProcessor WriteCommandAction]
   [com.intellij.openapi.command UndoConfirmationPolicy]
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.util Computable]
   [com.intellij.util ThrowableRunnable]))

(set! *warn-on-reflection* true)

(defn invoke-later!
  "API for `Application/invokeLater`.
   Returns a promise which can be `deref` to await the result of `invoke-fn` execution.

   ref: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/application/Application.java#L373"
  [{:keys [invoke-fn ^ModalityState modality-state]
    :or {modality-state (ModalityState/defaultModalityState)}}]
  (let [p (promise)]
    (.invokeLater
     (ApplicationManager/getApplication)
     (fn []
       (deliver p (invoke-fn)))
     modality-state)
    p))

(defn read-action!
  "API for `Application/runReadAction`.
   Returns a promise which can be `deref` to await the result of `run-fn` execution.

   ref: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/application/Application.java#L112"
  [{:keys [run-fn]}]
  (let [p (promise)]
    (.runReadAction
     (ApplicationManager/getApplication)
     (reify Computable
       (compute [_]
         (let [result (run-fn)]
           (deliver p result)
           result))))
    p))

(defn write-action!
  "API for `Application/runWriteAction`.
   Returns a promise which can be `deref` to await the result of `run-fn` execution.

   ref: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/application/Application.java#L157"
  [{:keys [run-fn]}]
  (let [p (promise)]
    (.runWriteAction
     (ApplicationManager/getApplication)
     (reify Computable
       (compute [_]
         (let [result (run-fn)]
           (deliver p result)
           result))))
    p))

(defn execute-command!
  "API for `CommandProcessor/executeCommand`.
   Returns a promise which can be `deref` to await the result of `command-fn` execution.

   ref: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/command/CommandProcessor.java"
  [{:keys [^String name group-id ^Project project command-fn
           ^UndoConfirmationPolicy undo-confirmation-policy
           ^Boolean record-command-for-active-document?]
    :or {undo-confirmation-policy UndoConfirmationPolicy/DEFAULT
         record-command-for-active-document? false}}]
  (let [p (promise)]
    (.executeCommand
     (CommandProcessor/getInstance)
     project
     (fn []
       (let [result (command-fn)]
         (deliver p result)
         result))
     name
     group-id
     undo-confirmation-policy
     record-command-for-active-document?)
    p))

(defn write-command-action
  "API for `WriteCommandAction/writeCommandAction`."
  [^Project project run-fn]
  (.run (WriteCommandAction/writeCommandAction project)
        (reify ThrowableRunnable
          (run [_]
            (run-fn)))))
