(ns com.github.clojure-repl.intellij.key-listener
  (:require
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.openapi.editor Editor]
   [java.awt KeyEventDispatcher KeyboardFocusManager]
   [java.awt.event KeyEvent]))

(set! *warn-on-reflection* true)

(defonce ^:private key-listeners* (atom {}))

(defn register-for-editor! [{:keys [editor on-key-pressed]}]
  (let [dispatcher (proxy+ [] KeyEventDispatcher
                     (dispatchKeyEvent [_ ^KeyEvent event]
                       (if (= KeyEvent/KEY_PRESSED (.getID event))
                         (boolean (on-key-pressed event))
                         false)))]
    (swap! key-listeners* assoc editor dispatcher)
    (.addKeyEventDispatcher
     (KeyboardFocusManager/getCurrentKeyboardFocusManager)
     dispatcher)))

(defn unregister-editor! [^Editor editor-to-unregister]
  (doseq [[^Editor editor dispatcher] @key-listeners*]
    (when (= editor-to-unregister editor)
      (.removeKeyEventDispatcher (KeyboardFocusManager/getCurrentKeyboardFocusManager) dispatcher)
      (swap! key-listeners* dissoc editor))))
