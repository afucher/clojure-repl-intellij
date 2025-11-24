(ns com.github.ericdallo.clj4intellij.listener.class-loader
  (:gen-class
   :name com.github.ericdallo.clj4intellij.listener.ClojureClassLoaderListener
   :extends com.github.ericdallo.clj4intellij.ClojureClassLoader
   :implements [com.intellij.ide.AppLifecycleListener])
  (:require
   [clojure.java.io :as io])
  (:import
   [com.github.ericdallo.clj4intellij ClojureClassLoader]))

(set! *warn-on-reflection* true)

(defn -appFrameCreated [_ _]
  (ClojureClassLoader/bind)
  (last (re-find #"<name>(.+)</name>" (slurp (io/resource "META-INF/plugin.xml")))))

(defn -welcomeScreenDisplayed [_])
(defn -appStarted [_])
(defn -projectFrameClosed [_])
(defn -projectOpenFailed [_])
(defn -appStarting [_ _])
(defn -appClosing [_])
(defn -appWillBeClosed [_ _])
