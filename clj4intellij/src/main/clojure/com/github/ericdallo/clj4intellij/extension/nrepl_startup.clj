(ns com.github.ericdallo.clj4intellij.extension.nrepl-startup
  (:gen-class
   :name com.github.ericdallo.clj4intellij.extension.NREPLStartup
   :implements [com.intellij.openapi.startup.StartupActivity
                com.intellij.openapi.project.DumbAware])
  (:require
   [com.github.ericdallo.clj4intellij.config :as plugin]
   [com.github.ericdallo.clj4intellij.logger :as logger])
  (:import
   [com.github.ericdallo.clj4intellij ClojureClassLoader]
   [com.intellij.openapi.project Project]
   [java.net ServerSocket]))

(set! *warn-on-reflection* true)

(defn get-open-port []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn -runActivity [_this ^Project _]
  (ClojureClassLoader/bind)
  (if (plugin/nrepl-support?)
    (let [port (or (plugin/nrepl-port)
                   (get-open-port))]
      (logger/info "Starting nrepl server on port" port "...")
      (try
        ((requiring-resolve 'nrepl.server/start-server)
         :port port)
        (logger/info "Started nrepl server at port" port)
        (catch Exception e
          (logger/warn "Could not start nrepl server, error:" e))))
    (logger/info "Skipping nrepl server start, no config found.")))
