(ns com.github.clojure-repl.intellij.listener.app-lifecycle
  (:gen-class
   :name com.github.clojure_repl.intellij.listener.AppLifecycleListener
   :extends com.github.clojure_repl.intellij.WithLoader
   :implements [com.intellij.ide.AppLifecycleListener])
  (:require
   [com.github.clojure-repl.intellij.logger :as logger])
  (:import
   [com.github.clojure_repl.intellij WithLoader]))

(set! *warn-on-reflection* true)

(defn ^:private start-nrepl-server [port]
  (try
    ((requiring-resolve 'nrepl.server/start-server)
     :port port)
    (logger/info "Started nrepl server at port %s" port)
    (catch Exception e
      (logger/warn "No debug nrepl found %s" e))))

(defn -appFrameCreated [_ _]
  (WithLoader/bind)
  (start-nrepl-server 7770))

(defn -welcomeScreenDisplayed [_])
(defn -appStarted [_])
(defn -appStarting [_ _])
(defn -projectFrameClosed [_])
(defn -projectOpenFailed [_])
(defn -appClosing [_])
(defn -appWillBeClosed [_ _])
