(ns com.github.clojure-repl.intellij.tool-window.repl-messages
  (:gen-class
   :name com.github.clojure_repl.intellij.tool_window.ReplMessagesToolWindow
   :implements [com.intellij.openapi.wm.ToolWindowFactory
                com.intellij.openapi.project.DumbAware])
  (:require
   [clojure.java.io :as io]
   [clojure.walk :as walk]
   [com.github.ericdallo.clj4intellij.logger :as logger]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.github.clojure_repl.intellij Icons]
   [com.intellij.execution.filters TextConsoleBuilderImpl]
   [com.intellij.execution.impl ConsoleViewImpl]
   [com.intellij.openapi Disposable]
   [com.intellij.openapi.project Project]
   [com.intellij.openapi.ui SimpleToolWindowPanel]
   [com.intellij.openapi.wm ToolWindow ToolWindowAnchor]
   [java.io File]))

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

(defn console [^Project project]
  (proxy+ [project false] ConsoleViewImpl))

(defn createUI [^SimpleToolWindowPanel panel ^Project project]
  (logger/info ">>>>>> panel" panel)
  (let [console ^TextConsoleBuilderImpl (proxy+ [project]
                                                TextConsoleBuilderImpl
                                                (createConsole [] (console project)))]
    (.setContent panel console)))

(defn messages-window ^SimpleToolWindowPanel [^Project project]
  (logger/info ">>>>>> messages-window")
  (doto (proxy+ [false true]
                SimpleToolWindowPanel
                Disposable
                (dispose [] (logger/info "Disposing REPL messages tool window"))
                #_(createUI this project))
    (createUI project)))

(defn -init [_ ^ToolWindow _tool-window] [])

(defn -manager [_ _ _])

(defn -shouldBeAvailable [_ _] true)

(defn -createToolWindowContent [_ ^Project project ^ToolWindow tool-window]
  (let [content-manager (.getContentManager tool-window)
        window (messages-window project)
        content (-> content-manager .getFactory (.createContent window "teste" false))]
    (.setDisposer content window)
    (.addContent content-manager content)))

(defn -isApplicableAsync
  ([_ ^Project project]
   (any-clj-files? (.getBasePath project)))
  ([_ ^Project project _]
   (-isApplicableAsync _ project)))

(defn -getIcon [_] Icons/CLOJURE_REPL)

(defn -getAnchor [_] ToolWindowAnchor/BOTTOM)

(defn -manage [_ _ _ _])
