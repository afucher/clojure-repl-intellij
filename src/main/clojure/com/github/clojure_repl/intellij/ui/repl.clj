(ns com.github.clojure-repl.intellij.ui.repl
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.ui.color :as ui.color]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.intellij.openapi.project Project]
   [java.awt.event InputEvent KeyEvent]
   [java.time.format DateTimeFormatter]
   [javax.swing JTextArea]))

(set! *warn-on-reflection* true)

(def ^:private code-to-eval-regexp
  #"(?mx)                    # match multiline and allow comments
    (^[\w-.]+>\s*)        # group1: match the prompt
    ([\s\S]*?)               # group2: match entry
                             # - we use [\s\S] instead of . because . does not match newlines
    (?=\n^[\w-.]+>\s*|\z) # lookahead for next prompt or end of string")

(defn ^:private extract-code-to-eval [repl-content-text]
  (or (some-> (re-seq code-to-eval-regexp repl-content-text)
              last
              last
              string/trim)
      ""))

(defn ^:private on-repl-input [project ^KeyEvent key-event on-eval]
  (.consume key-event)
  (let [repl-content ^JTextArea (.getComponent key-event)
        repl-content-text (seesaw/text repl-content)
        code-to-eval (extract-code-to-eval repl-content-text)
        last-output (db/get-in project [:console :state :last-output])
        entries (db/get-in project [:current-nrepl :entry-history])]
    (seesaw/text! repl-content (str last-output code-to-eval))
    (db/assoc-in! project [:current-nrepl :entry-index] -1)
    (when-not (or (string/blank? code-to-eval)
                  (= code-to-eval (first entries)))
      (db/update-in! project [:current-nrepl :entry-history] #(conj % code-to-eval)))
    (let [{:keys [value out err]} (on-eval code-to-eval)
          result-text (str
                       (when err (str "\n" err))
                       (when out (str "\n" out))
                       (when value (str "\n;; => " (last value))))
          ns-text (str "\n" (db/get-in project [:current-nrepl :ns]) "> ")]
      (.append repl-content (str result-text ns-text)))
    (let [new-text (seesaw/text repl-content)]
      (.setCaretPosition repl-content (count new-text))
      (db/assoc-in! project [:console :state :last-output] new-text))))

(defn history-up
  [project]
  (let [entries (db/get-in project [:current-nrepl :entry-history])
        current-index (db/get-in project [:current-nrepl :entry-index])]
    (when (and (pos? (count entries))
               (< current-index (dec (count entries))))
      (let [entry (nth entries (inc current-index))
            console (db/get-in project [:console :ui])
            repl-content (seesaw/select console [:#repl-content])
            last-output (db/get-in project [:console :state :last-output])]
        (db/update-in! project [:current-nrepl :entry-index] inc)
        (seesaw/config! repl-content :text (str last-output entry))))))

(defn history-down
  [project]
  (let [entries (db/get-in project [:current-nrepl :entry-history])
        current-index (db/get-in project [:current-nrepl :entry-index])]
    (when (and (pos? (count entries))
               (> current-index 0))
      (let [entry (nth entries (dec current-index))
            console (db/get-in project [:console :ui])
            repl-content (seesaw/select console [:#repl-content])
            last-output (db/get-in project [:console :state :last-output])]
        (db/update-in! project [:current-nrepl :entry-index] dec)
        (seesaw/config! repl-content :text (str last-output entry))))))

(defn ^:private on-repl-history-entry [project ^KeyEvent key-event]
  (let [page-up? (= KeyEvent/VK_PAGE_UP (.getKeyCode key-event))
        page-down? (= KeyEvent/VK_PAGE_DOWN (.getKeyCode key-event))
        entries (db/get-in project [:current-nrepl :entry-history])]
    (when (pos? (count entries))
      (when page-up?
        (history-up project))
      (when page-down?
        (history-down project)))))

(defn ^:private on-repl-new-line [^KeyEvent key-event]
  (.consume key-event)
  (.append ^JTextArea (.getComponent key-event) "\n"))

(defn ^:private ns-text [project]
  (str (db/get-in project [:current-nrepl :ns]) "> "))

(defn ^:private initial-text+ns [project initial-text]
  (str initial-text "\n\n" (ns-text project)))

(defn clear-repl [^Project project console]
  (let [text (str ";; Output cleared\n" (ns-text project))]
    (seesaw/text! (seesaw/select console [:#repl-content]) text)
    (db/assoc-in! project [:console :state :last-output] text)))

(defn ^:private on-repl-clear [project ^KeyEvent key-event]
  (.consume key-event)
  (clear-repl project (.getComponent key-event)))

(defn build-console [project {:keys [initial-text on-eval]}]
  (db/assoc-in! project [:console :state] {:status :disabled
                                           :initial-text initial-text
                                           :last-output ""})
  (seesaw/scrollable
   (mig/mig-panel
    :id :repl-input-layout
    :constraints ["fill"]
    :background (.getBackgroundColor (ui.color/repl-window))
    :items [[(seesaw/text
              :id :repl-content
              :multi-line? true
              :editable? true
              :background (.getBackgroundColor (ui.color/repl-window))
              :text (db/get-in project [:console :state :last-output])
              :listen [:key-pressed (fn [^KeyEvent event]
                                      (if (identical? :enabled (db/get-in project [:console :state :status]))
                                        (let [ctrl? (not= 0 (bit-and (.getModifiers event) InputEvent/CTRL_MASK))
                                              shift? (not= 0 (bit-and (.getModifiers event) InputEvent/SHIFT_MASK))
                                              enter? (= KeyEvent/VK_ENTER (.getKeyCode event))
                                              l? (= KeyEvent/VK_L (.getKeyCode event))
                                              page-up? (= KeyEvent/VK_PAGE_UP (.getKeyCode event))
                                              page-down? (= KeyEvent/VK_PAGE_DOWN (.getKeyCode event))]
                                          (cond
                                            (or (and ctrl? page-up?) (and ctrl? page-down?))
                                            (on-repl-history-entry project event)

                                            (and shift? enter?)
                                            (on-repl-new-line event)

                                            (and enter? (not shift?))
                                            (on-repl-input project event on-eval)

                                            (and ctrl? l?)
                                            (on-repl-clear project event)))
                                        (.consume event)))]) "grow"]])))

(defn set-initial-text [project console text]
  (db/assoc-in! project [:console :state] {:status :enabled
                                           :initial-text text
                                           :last-output (initial-text+ns project text)})
  (let [repl-content (seesaw/select console [:#repl-content])
        ns-text (str "\n\n" (db/get-in project [:current-nrepl :ns]) "> ")]
    (.setText ^JTextArea repl-content "")
    (.append ^JTextArea repl-content (str text ns-text))))

(def ^:private ^DateTimeFormatter time-formatter (DateTimeFormatter/ofPattern "dd/MM/yyyy HH:mm:ss"))

(defn close-console [project console]
  (let [repl-content (seesaw/select console [:#repl-content])]
    (db/assoc-in! project [:console :state :status] :disabled)
    (seesaw/config! repl-content :editable? false)
    (.append ^JTextArea repl-content (format "\n*** Closed on %s ***" (.format time-formatter (java.time.LocalDateTime/now))))))

(defn append-text [console text]
  (let [repl-content (seesaw/select console [:#repl-content])
        text (ui.color/remove-ansi-color text)]
    (.append ^JTextArea repl-content text)))

(defn append-result-text [project console text]
  (append-text console (str "\n" text (db/get-in project [:current-nrepl :ns]) "> ")))
