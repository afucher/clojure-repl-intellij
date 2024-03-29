(ns com.github.clojure-repl.intellij.ui.repl
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.db :as db]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [java.awt.event InputEvent KeyEvent]
   [java.time.format DateTimeFormatter]
   [javax.swing JTextArea]))

(set! *warn-on-reflection* true)

(def ^:private code-to-eval-regexp #".*>\s+([^>]+)$")
(def ^:private color-repl-primary "#1d252c")

(defonce ^:private console-state* (atom {:last-output nil}))

(defn ^:private extract-code-to-eval [repl-content-text]
  (or (some-> (re-find code-to-eval-regexp repl-content-text)
              last
              string/trim)
      ""))

(defn ^:private on-repl-input [project ^KeyEvent key-event on-eval]
  (.consume key-event)
  (let [repl-content ^JTextArea (.getComponent key-event)
        repl-content-text (seesaw/text repl-content)
        code-to-eval (extract-code-to-eval repl-content-text)]
    (seesaw/text! repl-content (str (:last-output @console-state*) code-to-eval))
    (let [{:keys [value out err]} (on-eval code-to-eval)
          result-text (str
                       (when err (str "\n" err))
                       (when out (str "\n" out))
                       (when value (str "\n;; => " value)))
          ns-text (str "\n" (db/get-in project [:current-nrepl :ns]) "> ")]
      (.append repl-content (str result-text ns-text)))
    (let [new-text (seesaw/text repl-content)]
      (.setCaretPosition repl-content (count new-text))
      (swap! console-state* assoc :last-output new-text))))

(defn ^:private on-repl-new-line [^KeyEvent key-event]
  (.consume key-event)
  (.append ^JTextArea (.getComponent key-event) "\n"))

(defn ^:private initial-text+ns [project initial-text]
  (str initial-text "\n\n" (db/get-in project [:current-nrepl :ns]) "> "))

(defn ^:private on-repl-clear [project ^KeyEvent key-event]
  (.consume key-event)
  (let [text (initial-text+ns project (:initial-text @console-state*))]
    (seesaw/text! (.getComponent key-event) text)
    (swap! console-state* assoc :last-output text)))

(defn build-console [project {:keys [initial-text on-eval]}]
  (reset! console-state* {:status :disabled
                          :initial-text initial-text
                          :last-output ""})
  (seesaw/scrollable
   (mig/mig-panel
    :id :repl-input-layout
    :constraints ["fill"]
    :background color-repl-primary
    :items [[(seesaw/text
              :id :repl-content
              :multi-line? true
              :editable? true
              :background color-repl-primary
              :text (:last-output @console-state*)
              :listen [:key-pressed (fn [^KeyEvent event]
                                      (if (identical? :enabled (:status @console-state*))
                                        (let [ctrl? (not= 0 (bit-and (.getModifiers event) InputEvent/CTRL_MASK))
                                              shift? (not= 0 (bit-and (.getModifiers event) InputEvent/SHIFT_MASK))
                                              enter? (= KeyEvent/VK_ENTER (.getKeyCode event))
                                              l? (= KeyEvent/VK_L (.getKeyCode event))]
                                          (cond
                                            (and shift? enter?)
                                            (on-repl-new-line event)

                                            (and enter? (not shift?))
                                            (on-repl-input project event on-eval)

                                            (and ctrl? l?)
                                            (on-repl-clear project event)))
                                        (.consume event)))]) "grow"]])))

(defn set-initial-text [project console text]
  (swap! console-state* assoc
         :status :enabled
         :initial-text text
         :last-output (initial-text+ns project text))
  (let [repl-content (seesaw/select console [:#repl-content])
        ns-text (str "\n\n" (db/get-in project [:current-nrepl :ns]) "> ")]
    (.setText ^JTextArea repl-content "")
    (.append ^JTextArea repl-content (str text ns-text))))

(def ^:private ^DateTimeFormatter time-formatter (DateTimeFormatter/ofPattern "dd/MM/yyyy HH:mm:ss"))

(defn close-console [console]
  (let [repl-content (seesaw/select console [:#repl-content])]
    (swap! console-state* assoc :status :disabled)
    (seesaw/config! repl-content :editable? false)
    (.append ^JTextArea repl-content (format "\n*** Closed on %s ***" (.format time-formatter (java.time.LocalDateTime/now))))))

(defn append-text [console text]
  (let [repl-content (seesaw/select console [:#repl-content])]
    (.append ^JTextArea repl-content text)))

(defn append-result-text [project console text]
  (append-text console (str "\n" text (db/get-in project [:current-nrepl :ns]) "> ")))
