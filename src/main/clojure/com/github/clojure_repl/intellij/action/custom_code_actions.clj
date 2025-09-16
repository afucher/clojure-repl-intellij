(ns com.github.clojure-repl.intellij.action.custom-code-actions
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.set :as set]
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.action.eval :as a.eval]
   [com.github.clojure-repl.intellij.actions :as actions]
   [com.github.clojure-repl.intellij.app-info :as app-info]
   [com.github.clojure-repl.intellij.editor :as editor]
   [com.github.clojure-repl.intellij.parser :as parser]
   [com.github.ericdallo.clj4intellij.action :as action]
   [com.github.ericdallo.clj4intellij.util :as util]
   [com.rpl.proxy-plus :refer [proxy+]]
   [rewrite-clj.zip :as z])
  (:import
   [com.github.clojure_repl.intellij Icons]
   [com.intellij.openapi.actionSystem
    ActionManager
    AnAction
    AnActionEvent
    CommonDataKeys
    DefaultActionGroup]
   [com.intellij.openapi.editor Editor]
   [com.intellij.openapi.project DumbAwareAction Project]
   [javax.swing Icon]))

(set! *warn-on-reflection* true)

(def ^:const group-id "ClojureREPL.Custom")

(defn ^:private current-var [^Editor editor]
  (let [[row col] (util/editor->cursor-position editor)
        text (.getText (.getDocument editor))
        root-zloc (z/of-string text)
        current-var (some-> (parser/find-var-at-pos root-zloc (inc row) col) z/string)]
    current-var))

(defn ^:private top-level-form [^Editor editor]
  (let [[row col] (util/editor->cursor-position editor)
        text (.getText (.getDocument editor))
        root-zloc (z/of-string text)]
    (some-> (parser/find-form-at-pos root-zloc (inc row) col) parser/to-top z/string)))

(def available-vars #{:current-var :file-namespace :selection :top-level-form})

(set! *warn-on-reflection* false)
(defn ^:private group-children
  "Newer versions of IntelliJ (2025) changed the getChildren signature
   This function checks in runtime the version before call"
  [^DefaultActionGroup group]
  (if (app-info/at-least-version? "252.13776.59")
    (.getChildren group ^ActionManager (ActionManager/getInstance))
    (.getChildren group nil ^ActionManager (ActionManager/getInstance))))
(set! *warn-on-reflection* true)

(defn custom-action [^AnActionEvent event code-snippet]
  (let [action-name (-> event .getPresentation .getText)
        editor ^Editor (.getData event CommonDataKeys/EDITOR_EVEN_IF_INACTIVE)
        required-vars (set/select (fn [v] (string/includes? code-snippet (str "$" (name v)))) available-vars)
        selection (-> editor .getSelectionModel .getSelectedText)
        current-file-ns (some-> (editor/ns-form editor) parser/find-namespace z/string parser/remove-metadata)
        fqn-current-var (when-let [current-var (current-var editor)]
                          (str (or current-file-ns "user") "/" current-var))
        top-level-form (top-level-form editor)
        code (cond-> (str code-snippet)
               (contains? required-vars :selection) (string/replace #"\$selection" selection)
               (contains? required-vars :current-var) (string/replace #"\$current-var" fqn-current-var)
               (contains? required-vars :file-namespace) (string/replace #"\$file-namespace" current-file-ns)
               (contains? required-vars :top-level-form) (string/replace #"\$top-level-form" top-level-form))]
    (a.eval/eval-custom-code-action event action-name code)))

(defn register-custom-code-actions
  ([eval-code-actions]
   (register-custom-code-actions eval-code-actions nil))
  ([eval-code-actions ^Project project]
   (doseq [action eval-code-actions]
     (let [id (str "ClojureREPL.Custom." (some-> project .getName csk/->PascalCase (str ".")) (csk/->PascalCase (:name action)))
           title ^String (:name action)
           description ^String (:name action)
           icon ^Icon Icons/CLOJURE_REPL
           an-action (proxy+
                      [title description icon]
                      DumbAwareAction
                      (update
                       [_ ^AnActionEvent event]
                       (when project
                         (let [action-project (actions/action-event->project event)]
                           (.setEnabled (.getPresentation event) (boolean (= project action-project))))))
                      (actionPerformed
                       [_ event]
                       (custom-action event (:code action))))

           custom-action-group ^DefaultActionGroup (.getAction (ActionManager/getInstance) group-id)]
       (action/register-action!
        :id id
        :action an-action)
       (let [children (group-children custom-action-group)]
         (when (not-any? (fn [a] (= (-> ^AnAction a .getTemplatePresentation .getText)
                                    (-> ^AnAction an-action .getTemplatePresentation .getText)))
                         children)
           (.add custom-action-group an-action)))))))


(comment
  (def action-manager (ActionManager/getInstance))
  (def custom-action-group (.getAction action-manager group-id))
  (def ids (.getActionIdList action-manager "ClojureREPL.Custom"))

  (defn debug-group-actions [^String group-id]
    (let [am (ActionManager/getInstance)
          group (.getAction am group-id)]
      (when (instance? DefaultActionGroup group)
        (doseq [a (group-children ^DefaultActionGroup group)]
          (println "Action:" a
                   "ID:" (.getId am a)
                   "Text:" (.getText (.getTemplatePresentation a)))))))


  (debug-group-actions group-id)
  #_())
