(ns com.github.clojure-repl.intellij.keyboard-manager
  (:require
   [com.github.ericdallo.clj4intellij.app-manager :as app-manager]
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.find SearchReplaceComponent]
   [com.intellij.ide IdeEventQueue]
   [com.intellij.openapi.editor Editor]
   [java.awt KeyEventDispatcher KeyboardFocusManager]
   [java.awt.event KeyEvent]))

(set! *warn-on-reflection* true)

(defonce ^:private key-listeners* (atom {}))

(defn register-listener-for-editor! [{:keys [^Editor editor on-key-pressed]}]
  (let [dispatcher (proxy+ [] KeyEventDispatcher

                     (dispatchKeyEvent [_ ^KeyEvent event]
                       (if (and (= KeyEvent/KEY_PRESSED (.getID event))
                                (or (= (.getContentComponent editor) (.getComponent event))
                                    ;; Intellij shows the searchReplace ignoring this listener
                                    ;; so we send the event even if this search window compoennt
                                    ;; was opened.
                                    (= SearchReplaceComponent (type (.getComponent event)))))
                         (boolean (on-key-pressed event))
                         false)))]
    (swap! key-listeners* assoc editor dispatcher)
    (.addKeyEventDispatcher
     (KeyboardFocusManager/getCurrentKeyboardFocusManager)
     dispatcher)))

(defn unregister-listener-for-editor! [^Editor editor-to-unregister]
  (doseq [[^Editor editor dispatcher] @key-listeners*]
    (when (= editor-to-unregister editor)
      (.removeKeyEventDispatcher (KeyboardFocusManager/getCurrentKeyboardFocusManager) dispatcher)
      (swap! key-listeners* dissoc editor))))

(defn send-key-pressed! [^Editor editor ^Integer key-code]
  (app-manager/invoke-later!
   {:invoke-fn (fn []
                 (.dispatchEvent (IdeEventQueue/getInstance)
                                 (KeyEvent. (.getContentComponent editor)
                                            KeyEvent/KEY_PRESSED
                                            (System/currentTimeMillis)
                                            0
                                            key-code
                                            (char key-code))))}))
