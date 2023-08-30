(ns com.github.clojure-repl.intellij.listener.app-lifecycle
  (:gen-class
   :name com.github.clojure_repl.intellij.listener.AppLifecycleListener
   :extends com.github.clojure_repl.intellij.WithLoader
   :implements [com.intellij.ide.AppLifecycleListener])
  (:import
   [com.github.clojure_repl.intellij WithLoader]))

(defn -appFrameCreated [_ _] (WithLoader/bind))

(defn -welcomeScreenDisplayed [_])
(defn -appStarted [_])
(defn -appStarting [_ _])
(defn -projectFrameClosed [_])
(defn -projectOpenFailed [_])
(defn -appClosing [_])
(defn -appWillBeClosed [_ _])
