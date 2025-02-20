(ns com.github.clojure-repl.intellij.ui.repl
  (:require
   [clojure.string :as string]
   [com.github.clojure-repl.intellij.db :as db]
   [com.github.clojure-repl.intellij.keyboard-manager :as key-manager]
   [com.github.clojure-repl.intellij.ui.color :as ui.color]
   [com.github.clojure-repl.intellij.ui.components :as ui.components]
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [seesaw.core :as seesaw]
   [seesaw.mig :as mig])
  (:import
   [com.intellij.openapi.editor ScrollType]
   [com.intellij.openapi.editor.colors EditorColorsManager EditorFontType]
   [com.intellij.openapi.editor.ex EditorEx]
   [com.intellij.openapi.project Project]
   [com.intellij.ui EditorTextField]
   [com.intellij.util.ui UIUtil]
   [java.awt.event InputEvent KeyEvent]
   [java.time.format DateTimeFormatter]))

(set! *warn-on-reflection* true)

(defn ^:private replace-last [s target replacement]
  (let [idx (string/last-index-of s target)]
    (if (nil? idx)
      s
      (str (subs s 0 idx) replacement (subs s (+ idx (count target)))))))

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

(defn set-text
  ([repl-content text]
   (set-text repl-content text nil))
  ([^EditorTextField repl-content text {:keys [append? drop-last-newline-before-append?]}]
   (let [text (ui.color/remove-ansi-color text)
         dropped-text (if (and append? drop-last-newline-before-append?)
                        (replace-last (.getText repl-content) "\n" "")
                        (.getText repl-content))
         text (if append? (str dropped-text text) text)]
     (app-manager/invoke-later! {:invoke-fn (fn []
                                              (.setText repl-content text))}))))

(defn ^:private move-caret-and-scroll-to-latest [^EditorTextField repl-content]
  (app-manager/invoke-later!
   {:invoke-fn (fn []
                 (let [editor ^EditorEx (.getEditor repl-content)]
                   (.moveToOffset (.getCaretModel editor) (.getTextLength (.getDocument repl-content)))
                   (.scrollToCaret (.getScrollingModel editor) ScrollType/MAKE_VISIBLE)))}))

(defn ^:private on-repl-input [project on-eval ^KeyEvent event]
  (.consume event)
  (let [repl-content ^EditorTextField (seesaw/select (db/get-in project [:console :ui]) [:#repl-content])
        repl-content-text (.getText repl-content)
        code-to-eval (extract-code-to-eval repl-content-text)
        entries (db/get-in project [:current-nrepl :entry-history])]
    (db/assoc-in! project [:current-nrepl :entry-index] -1)
    (when-not (or (string/blank? code-to-eval)
                  (= code-to-eval (first entries)))
      (db/update-in! project [:current-nrepl :entry-history] #(conj % code-to-eval)))
    (let [{:keys [value out err]} (on-eval code-to-eval)
          result-text (str
                       (when err (str "\n" err))
                       (when out (str "\n" out))
                       (when value (str "\n;; => " (last value))))
          ns-text (str "\n" (db/get-in project [:current-nrepl :ns]) "> ")
          new-text (str result-text ns-text)]
      (set-text repl-content new-text {:append? true :drop-last-newline-before-append? true})
      (move-caret-and-scroll-to-latest repl-content)
      (app-manager/invoke-later!
       {:invoke-fn (fn []
                     (db/assoc-in! project [:console :state :last-output] (.getText repl-content)))})))
  true)

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
        (set-text repl-content (str last-output entry))
        (move-caret-and-scroll-to-latest repl-content)))))

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
        (set-text repl-content (str last-output entry))
        (move-caret-and-scroll-to-latest repl-content)))))

(defn ^:private on-repl-history-entry [project ^KeyEvent key-event]
  (let [page-up? (= KeyEvent/VK_PAGE_UP (.getKeyCode key-event))
        page-down? (= KeyEvent/VK_PAGE_DOWN (.getKeyCode key-event))
        entries (db/get-in project [:current-nrepl :entry-history])]
    (when (pos? (count entries))
      (when page-up?
        (history-up project))
      (when page-down?
        (history-down project))))
  true)

(defn ^:private on-repl-new-line [project]
  (set-text (seesaw/select (db/get-in project [:console :ui]) [:#repl-content]) "\n" {:append? true})
  true)

(defn ^:private ns-text [project]
  (str (db/get-in project [:current-nrepl :ns]) "> "))

(defn ^:private initial-text+ns [project initial-text]
  (str initial-text "\n\n" (ns-text project)))

(defn clear-repl [^Project project console]
  (let [text (str ";; Output cleared\n" (ns-text project))]
    (set-text (seesaw/select console [:#repl-content]) text)
    (db/assoc-in! project [:console :state :last-output] text)))

(defn ^:private on-repl-clear [project]
  (let [console (db/get-in project [:console :ui])]
    (clear-repl project console)
    (key-manager/send-key-pressed! (.getEditor ^EditorTextField (seesaw/select console [:#repl-content]))
                                   KeyEvent/VK_ESCAPE))
  true)

(defn build-console [project {:keys [initial-text on-eval]}]
  (db/assoc-in! project [:console :state] {:status :disabled
                                           :initial-text initial-text
                                           :last-output ""})
  (mig/mig-panel
   :id :repl-input-layout
   :constraints ["fill, insets 0"]
   :background (.getBackgroundColor (ui.color/repl-window))
   :items [[(ui.components/clojure-text-field
             :id :repl-content
             :project project
             :text (db/get-in project [:console :state :last-output])
             :background-color (.getBackgroundColor (ui.color/repl-window))
             :font (UIUtil/getFontWithFallback (.getFont (.getGlobalScheme (EditorColorsManager/getInstance))
                                                         (EditorFontType/forJavaStyle (.getFontType (ui.color/repl-window)))))
             :on-key-pressed (fn [^KeyEvent event]
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
                                     (on-repl-new-line project)

                                     (and enter? (not shift?))
                                     (on-repl-input project on-eval event)

                                     (and ctrl? l?)
                                     (on-repl-clear project)))
                                 false)))
            "grow"]]))

(defn set-initial-text [project console text]
  (db/assoc-in! project [:console :state] {:status :enabled
                                           :initial-text text
                                           :last-output (initial-text+ns project text)})
  (let [ns-text (str "\n\n" (db/get-in project [:current-nrepl :ns]) "> ")
        repl-content (seesaw/select console [:#repl-content])]
    (ui.components/init-clojure-text-field! repl-content)
    (set-text repl-content (str text ns-text) {:append? true})
    (move-caret-and-scroll-to-latest repl-content)))

(def ^:private ^DateTimeFormatter time-formatter (DateTimeFormatter/ofPattern "dd/MM/yyyy HH:mm:ss"))

(defn close-console [project console]
  (let [repl-content ^EditorTextField (seesaw/select console [:#repl-content])]
    (db/assoc-in! project [:console :state :status] :disabled)
    (.setViewer repl-content false)
    (set-text (seesaw/select console [:#repl-content]) (format "\n*** Closed on %s ***" (.format time-formatter (java.time.LocalDateTime/now))) {:append? true})
    (key-manager/unregister-listener-for-editor! (.getEditor repl-content))))

(defn append-result-text [project console text]
  (set-text (seesaw/select console [:#repl-content])
            (str "\n" text (db/get-in project [:current-nrepl :ns]) "> ")
            {:append? true}))
