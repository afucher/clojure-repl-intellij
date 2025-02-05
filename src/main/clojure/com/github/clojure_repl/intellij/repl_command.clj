(ns com.github.clojure-repl.intellij.repl-command
  (:require
   [babashka.fs :as fs]
   [clojure.java.shell :as shell]
   [clojure.string :as string]))

(set! *warn-on-reflection* true)

(def ^:private windows-os?
  (.contains (System/getProperty "os.name") "Windows"))

(def ^:private project-type->command
  {:lein (if windows-os? ["lein.bat"] ["lein"])
   :clojure ["clojure"]
   :babashka ["bb"]
   :shadow-cljs ["npx" "shadow-cljs"]
   :boot ["boot"]
   :nbb ["nbb"]
   :gradle ["./gradlew"]})

(def ^:private middleware-versions
  ;; TODO make version configurable in intellij settings
  {"nrepl/nrepl" "1.3.1"
   "cider/cider-nrepl" "0.55.7"})

(defn ^:private psh-cmd
  "Return the command vector that uses the PowerShell executable PSH to
  invoke CMD-AND-ARGS."
  ([psh & cmd-and-args]
   (into [psh "-NoProfile" "-Command"] cmd-and-args)))

(defn ^:private locate-executable
  "Locate and return the full path to the EXECUTABLE."
  [executable]
  (some-> ^java.nio.file.Path (fs/which executable) .toString))

(defn ^:private shell
  [& cmd-and-args]
  (println cmd-and-args)
  (apply shell/sh cmd-and-args))

(defn ^:private normalize-command
  "Return CLASSPATH-CMD, but with the EXEC expanded to its full path (if found).

  If the EXEC cannot be found, is one of clojure or lein and the
  program is running on MS-Windows, then, if possible, it tries to
  replace it with a PowerShell cmd invocation sequence in the
  following manner, while keeping ARGS the same.

  There could be two PowerShell executable available in the system
  path: powershell.exe (up to version 5.1, comes with windows) and/or
  pwsh.exe (versions 6 and beyond, can be installed on demand).

  If powershell.exe is available, it checks if the EXEC is installed
  in it as a module, and creates an invocation sequence as such to
  replace the EXEC. If not, it tries the same with pwsh.exe."
  [[exec & args :as cmd]]
  (if (and windows-os?
           (#{"clojure" "lein"} exec)
           (not (locate-executable exec)))
    (if-let [up (some #(when-let [ps (locate-executable %)]
                         (when (= 0 (:exit (apply shell (psh-cmd ps "Get-Command" exec))))
                           (psh-cmd ps exec)))
                      ["powershell" "pwsh"])]
      (into up args)
      cmd)
    cmd))

(defn ^:private project-type->parameters [project-type aliases jvm-args]
  (flatten
   (remove
    nil?
    (get
     {:lein ["update-in" ":dependencies" "conj" "[nrepl/nrepl \"%nrepl/nrepl%\"]"
             "--" "update-in" ":plugins" "conj" "[cider/cider-nrepl \"%cider/cider-nrepl%\"]"
             (when (seq jvm-args)
               ["--" "update-in" ":jvm-opts" "concat" (str (mapv #(str (first %) "=" (second %)) jvm-args))])
             "--" (when (seq aliases)
                    ["with-profile" (str "+" (string/join ",+" aliases))])
             "repl" ":headless" ":host" "localhost"]
      :clojure [(when (seq jvm-args)
                  (map #(str "-J" (first %) "=" (second %)) jvm-args))
                "-Sdeps"
                "'{:deps {nrepl/nrepl {:mvn/version \"\"\"%nrepl/nrepl%\"\"\"} cider/cider-nrepl {:mvn/version \"\"\"%cider/cider-nrepl%\"\"\"}} :aliases {:cider/nrepl {:main-opts [\"-m\" \"nrepl.cmdline\" \"--middleware\" \"[cider.nrepl/cider-middleware]\"]}}}'"
                (str "-M:" (string/join ":" (conj aliases "cider/nrepl")))]
      :babashka ["nrepl-server" "localhost:0"]
      :shadow-cljs ["server"]
      :boot ["repl" "-s" "-b" "localhost" "wait"]
      :nbb ["nrepl-server"]
      :gradle ["-Pdev.clojurephant.jack-in.nrepl=nrepl:nrepl:%nrepl/nrepl%,cider:cider-nrepl:%cider/cider-nrepl%"
               "clojureRepl"
               "--middleware=cider.nrepl/cider-middleware"]}
     project-type))))

(defn ^:private replace-versions [middleware-versions parameters]
  (reduce
   (fn [params [middleware version]]
     (mapv #(string/replace % (str "%" middleware "%") version) params))
   parameters
   middleware-versions))

(defn project->repl-start-command [project-type aliases jvm-args]
  (let [command (normalize-command (project-type->command project-type))
        parameters (replace-versions middleware-versions (project-type->parameters project-type aliases jvm-args))]
    (flatten (concat command parameters))))
