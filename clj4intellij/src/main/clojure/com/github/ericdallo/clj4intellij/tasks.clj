(ns com.github.ericdallo.clj4intellij.tasks
  (:require
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.openapi.progress ProgressIndicator ProgressManager Task$Backgroundable]))

(defn run-background-task! [project title run-fn]
  (.run (ProgressManager/getInstance)
        (proxy+
         [project title]
         Task$Backgroundable
          (run [_ ^ProgressIndicator indicator]
            (run-fn indicator)))))

(defn set-progress
  ([^ProgressIndicator indicator text]
   (.setText indicator text)
   (.setIndeterminate indicator true))
  ([^ProgressIndicator indicator text percentage]
   (.setText indicator text)
   (.setIndeterminate indicator false)
   (.setFraction indicator (double (/ percentage 100)))))
