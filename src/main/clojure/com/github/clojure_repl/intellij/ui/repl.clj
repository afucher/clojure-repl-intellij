(ns com.github.clojure-repl.intellij.ui.repl
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.keyboard-manager :as key-manager]
   [com.github.clojure-repl.intellij.ui.color :as ui.color]
   [com.github.clojure-repl.intellij.ui.components :as ui.components]
   [com.github.clojure-repl.intellij.ui.font :as ui.font]
   [com.github.clojure-repl.intellij.ui.text :as ui.text]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.intellij.openapi.editor ScrollType]
   [com.intellij.openapi.editor.ex EditorEx]
   [com.intellij.openapi.project Project]
   [com.intellij.ui EditorTextField]
   [java.awt.event InputEvent KeyEvent]
   [java.time.format DateTimeFormatter]))

(set! *warn-on-reflection* true)

(defn ^:private extract-input+code-to-eval [cur-ns ^String repl-content-text]
  (let [input (str cur-ns "> ")]
    (or (when-let [input+eval-text (some->> (re-seq (re-pattern (str cur-ns "> .*")) repl-content-text)
                                            last
                                            (string/last-index-of repl-content-text)
                                            (subs repl-content-text))]
          [input (string/trim-newline (string/replace input+eval-text input ""))])
        [input ""])))

(defn ^:private refresh-repl-text
  [^Project project]
  (let [repl-content ^EditorTextField (seesaw/select (db/get-in project [:console :ui]) [:#repl-content])
        input-text (db/get-in project [:console :state :last-input])
        output-text (db/get-in project [:console :state :last-output])
        final-text (if input-text
                     (str output-text "\n" input-text)
                     output-text)]
    (app-manager/invoke-later!
     {:invoke-fn (fn []
                   (.setText repl-content final-text)
                   (when-let [editor ^EditorEx (.getEditor repl-content)]
                     (.moveToOffset (.getCaretModel editor) (.getTextLength (.getDocument repl-content)))
                     (.scrollToCaret (.getScrollingModel editor) ScrollType/MAKE_VISIBLE)))}))) ;; guarantee that scroll happens after text is set

(defn ^:private set-output
  [^Project project output-text]
  (db/assoc-in! project [:console :state :last-output] (ui.text/remove-ansi-color output-text))
  (refresh-repl-text project))

(defn append-output
  [^Project project append-text]
  (let [last-output (db/get-in project [:console :state :last-output])]
    (set-output project (str last-output append-text))))

(defn ^:private set-temp-input
  [^Project project temp-input]
  (let [repl-content ^EditorTextField (seesaw/select (db/get-in project [:console :ui]) [:#repl-content])
        input-text (db/get-in project [:console :state :last-input])
        output-text (db/get-in project [:console :state :last-output])
        final-text (str output-text "\n" input-text temp-input)]
    (app-manager/invoke-later!
     {:invoke-fn (fn []
                   (.setText repl-content final-text)
                   (when-let [editor ^EditorEx (.getEditor repl-content)]
                     (.moveToOffset (.getCaretModel editor) (.getTextLength (.getDocument repl-content)))
                     (.scrollToCaret (.getScrollingModel editor) ScrollType/MAKE_VISIBLE)))}))) ;; Same here, maybe do a function for that?

(defn clear-input [project]
  (let [ns-text (str (db/get-in project [:current-nrepl :ns]) "> ")]
    (db/assoc-in! project [:console :state :last-input] ns-text)
    (refresh-repl-text project)))

(defn ^:private move-caret-and-scroll-to-latest [^EditorTextField repl-content]
  (app-manager/invoke-later!
   {:invoke-fn (fn []
                 (when-let [editor ^EditorEx (.getEditor repl-content)]
                   (.moveToOffset (.getCaretModel editor) (.getTextLength (.getDocument repl-content)))
                   (.scrollToCaret (.getScrollingModel editor) ScrollType/MAKE_VISIBLE)))}))


(defn ^:private on-repl-input [project on-eval ^KeyEvent event]
  (.consume event)
  (let [repl-content ^EditorTextField (seesaw/select (db/get-in project [:console :ui]) [:#repl-content])
        repl-content-text (.getText repl-content)
        cur-ns (db/get-in project [:current-nrepl :ns])
        [input code-to-eval] (extract-input+code-to-eval cur-ns repl-content-text)
        entries (db/get-in project [:current-nrepl :entry-history])]
    (db/assoc-in! project [:current-nrepl :entry-index] -1)
    (when-not (or (string/blank? code-to-eval)
                  (= code-to-eval (first entries)))
      (db/update-in! project [:current-nrepl :entry-history] #(conj % code-to-eval)))
    (let [{:keys [value ns out] :as response}  (on-eval code-to-eval)
          result-text (str
                       "\n" input code-to-eval
                        (when out (str "\n" out))
                        (when value (str "=> " (last value))))]
      (append-output project result-text)
      (when (and ns (not= ns cur-ns))
        (db/assoc-in! project [:current-nrepl :ns] ns)
        (doseq [fn (db/get-in project [:on-ns-changed-fns])]
          (fn project response)))))
  true)

(defn history-up
  [project]
  (let [entries (db/get-in project [:current-nrepl :entry-history])
        current-index (db/get-in project [:current-nrepl :entry-index])]
    (when (and (pos? (count entries))
               (< current-index (dec (count entries))))
      (let [entry (nth entries (inc current-index))]
        (db/update-in! project [:current-nrepl :entry-index] inc)
        (set-temp-input project entry)))))

(defn history-down
  [project]
  (let [entries (db/get-in project [:current-nrepl :entry-history])
        current-index (db/get-in project [:current-nrepl :entry-index])]
    (when (and (pos? (count entries))
               (> current-index 0))
      (let [entry (nth entries (dec current-index))]
        (db/update-in! project [:current-nrepl :entry-index] dec)
        (set-temp-input project entry)))))

(defn ^:private on-repl-new-line [project]
  (append-output project "\n")
  true)

(defn clear-repl [^Project project]
  (set-output project ";; Output cleared"))

(defn ^:private on-repl-clear [project]
  (clear-repl project)
  true)

(defn ^:private on-repl-backspace [project ^KeyEvent event]
  (let [ns (db/get-in project [:current-nrepl :ns])
        repl-content ^EditorTextField (seesaw/select (db/get-in project [:console :ui]) [:#repl-content])
        repl-lines (string/split-lines (.getText repl-content))
        last-repl-line (last repl-lines)
        repl-input (re-find (re-pattern (str ns "+>\\s")) last-repl-line)]
    (when-not repl-input
      (clear-input project)
      (.consume event))))

(defn build-console [project {:keys [initial-text on-eval]}]
  (db/assoc-in! project [:console :state :status] :disabled)
  (db/assoc-in! project [:console :state :initial-text] initial-text)
  (db/assoc-in! project [:console :state :last-output] "")
  (db/assoc-in! project [:console :state :last-input] nil)
  (mig/mig-panel
   :id :repl-input-layout
   :constraints ["fill, insets 0"]
   :background (.getBackgroundColor (ui.color/repl-window))
   :items [[(ui.components/clojure-text-field
             :id :repl-content
             :project project
             :text ""
             :background-color (.getBackgroundColor (ui.color/repl-window))
             :font (ui.font/code-font (ui.color/repl-window))
             :on-key-pressed (fn [^KeyEvent event]
                               (if (identical? :enabled (db/get-in project [:console :state :status]))
                                 (let [ctrl? (not= 0 (bit-and (.getModifiers event) InputEvent/CTRL_MASK))
                                       shift? (not= 0 (bit-and (.getModifiers event) InputEvent/SHIFT_MASK))
                                       enter? (= KeyEvent/VK_ENTER (.getKeyCode event))
                                       backspace? (= KeyEvent/VK_BACK_SPACE (.getKeyCode event))
                                       l? (= KeyEvent/VK_L (.getKeyCode event))]
                                   (cond

                                     (and shift? enter?)
                                     (on-repl-new-line project)

                                     (and enter? (not shift?))
                                     (on-repl-input project on-eval event)

                                     (and ctrl? l?)
                                     (on-repl-clear project)

                                     backspace?
                                     (on-repl-backspace project event)))
                                 false)))
            "grow"]]))

(defn set-repl-started-initial-text [project text]
  (let [output (str text "\n")]
    (db/assoc-in! project [:console :state :status] :enabled)
    (db/assoc-in! project [:console :state :initial-text] text)
    (append-output project output)
    (clear-input project)
    (refresh-repl-text project)))

(def ^:private ^DateTimeFormatter time-formatter (DateTimeFormatter/ofPattern "dd/MM/yyyy HH:mm:ss"))

(defn close-console [project console]
  (let [repl-content ^EditorTextField (seesaw/select console [:#repl-content])]
    (db/assoc-in! project [:console :state :status] :disabled)
    (db/assoc-in! project [:console :state :last-input] nil)
    (.setViewer repl-content false)
    (append-output project
                   (format "\n*** Closed on %s ***" (.format time-formatter (java.time.LocalDateTime/now))))
    (key-manager/unregister-listener-for-editor! (.getEditor repl-content))))
