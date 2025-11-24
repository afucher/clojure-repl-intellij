(ns com.github.ericdallo.clj4intellij.action
  (:require
   [com.rpl.proxy-plus :refer [proxy+]])
  (:import
   [com.intellij.openapi.actionSystem
    ActionManager
    Anchor
    Constraints
    DefaultActionGroup
    KeyboardShortcut]
   [com.intellij.openapi.keymap KeymapManager]
   [com.intellij.openapi.project DumbAwareAction]
   [javax.swing Icon KeyStroke]))

(set! *warn-on-reflection* true)

(defn register-action!
  "Dynamically register an action if not already registered."
  [& {:keys [id title description icon use-shortcut-of keyboard-shortcut on-performed action]
      :or {action #_{:clj-kondo/ignore [:destructured-or-binding-of-same-map]}
           (proxy+
            [^String title ^String description ^Icon icon]
            DumbAwareAction
             (actionPerformed [_ event] (on-performed event)))}}]
  (let [manager (ActionManager/getInstance)
        keymap-manager (KeymapManager/getInstance)
        keymap (.getActiveKeymap keymap-manager)]
    (when-not (.getAction manager id)
      (.registerAction manager id action)
      (when use-shortcut-of
        (.addShortcut keymap
                      id
                      (first (.getShortcuts (.getShortcutSet (.getAction manager use-shortcut-of))))))
      (when keyboard-shortcut
        (let [k-shortcut (KeyboardShortcut. (KeyStroke/getKeyStroke ^String (:first keyboard-shortcut))
                                            (some-> ^String (:second keyboard-shortcut) KeyStroke/getKeyStroke))]
          (when (empty? (.getShortcuts keymap id))
            (.addShortcut keymap id k-shortcut))
          (when (:replace-all keyboard-shortcut)
            (doseq [[conflict-action-id shortcuts] (.getConflicts keymap id k-shortcut)]
              (doseq [shortcut shortcuts]
                (.removeShortcut keymap conflict-action-id shortcut))))))
      action)))

(defn unregister-action [id]
  (let [manager (ActionManager/getInstance)]
    (when (.getAction manager id)
      (.unregisterAction manager id))))

(defn ^:private ->constraint ^Constraints [anchor relative-to]
  (Constraints. (case anchor
                  :first Anchor/FIRST
                  :last Anchor/LAST
                  :before Anchor/BEFORE
                  :after Anchor/AFTER) relative-to))

(defn register-group!
  "Dynamically register an action group if not registered yet."
  [& {:keys [id popup ^String text icon children]}]
  (let [group (DefaultActionGroup.)
        manager (ActionManager/getInstance)]
    (when-not (.getAction manager id)
      (when popup
        (.setPopup group popup))
      (when text
        (.setText (.getTemplatePresentation group) text))
      (when icon
        (.setIcon (.getTemplatePresentation group) icon))
      (.registerAction manager id group)
      (doseq [{:keys [type group-id anchor relative-to ref]} children]
        (case type
          :add-to-group (.add ^DefaultActionGroup (.getAction manager group-id) group (->constraint anchor relative-to))
          :reference (.add group (.getAction manager ref))
          :separator (.addSeparator group)))
      group)))
