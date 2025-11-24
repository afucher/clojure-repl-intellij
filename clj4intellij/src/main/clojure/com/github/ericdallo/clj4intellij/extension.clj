(ns com.github.ericdallo.clj4intellij.extension
  (:require
   [camel-snake-kebab.core :as csk]))

(set! *warn-on-reflection* true)

(defn ^:private flatten-1 [coll]
  (mapcat #(if (sequential? %) % [%]) coll))

(defn ^:private interface? [clazz]
  (and (class? clazz) (.isInterface ^Class clazz)))

(defmacro def-extension
  "Defines a extension for plugin.xml, using `gen-class` to create a class
   that extends or implements the provided `super-class`.

   The generated class is only generated at compile time but the methods
   can be re-defined.

   Example:
   ```clojure
   (def-extension ClojureBraceMatcher []
     PairedBraceMatcher
     (getPairs [this _]
       [,,,])
     (isPairedBracesAllowedBeforeType [this _ context-type]
       (boolean context-type)))
   ```"
  [name super-args super-class & methods]
  (let [super-class-resolved (resolve super-class)
        prefix (str name "_")
        interface? (interface? super-class-resolved)
        init-method (when (seq super-args)
                      (list 'defn (symbol (str prefix "init")) [] [super-args nil]))
        gen-class-args (->> [:name (str (csk/->snake_case (str *ns*)) "." name)
                             :prefix prefix
                             (when (seq super-args) [(symbol "init") (str prefix "init")])
                             (if interface?
                               [:implements [super-class-resolved]]
                               [:extends super-class-resolved])]
                            (remove nil?)
                            (flatten-1))
        method-defs (map (fn [[name args & body]]
                           (concat ['defn (symbol (str prefix name)) args] body))
                         methods)]
    `(do
       ~init-method
       ~@method-defs
       (gen-class ~@gen-class-args))))
