(ns com.github.clojure-repl.intellij.db)

(defonce db* (atom {:current-nrepl {:session-id nil
                                    :ns "user"}
                    :settings {:nrepl-port nil
                               :nrepl-host "localhost"
                               :remote-repl-mode :manual-config}
                    :on-repl-file-loaded-fns []
                    :on-repl-evaluated-fns []
                    :on-test-failed-fns []
                    :on-test-succeeded-fns []
                    :ops {}}))
