(ns com.github.clojure-repl.intellij.interop)

;; TODO move to clj4intellij
(defn new-class
  "Dynamically resolves the class-name in class loader and creates a new instance.
   Workaround to call generated java classes via gen-class from Clojure code itself."
  [class-name & args]
  (clojure.lang.Reflector/invokeConstructor (Class/forName class-name) (into-array Object args)))
