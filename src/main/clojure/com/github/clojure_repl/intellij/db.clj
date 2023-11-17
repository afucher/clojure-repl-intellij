(ns com.github.clojure-repl.intellij.db)

(defonce db* (atom {:current-nrepl {:session-id nil
                                    :ns "user"}
                    :settings {:nrepl-port nil
                               :nrepl-host "localhost"
                               :mode "manual"}
                    :on-repl-file-loaded-fns []
                    :on-repl-evaluated-fns []
                    :ops {}}))
