(ns com.github.clojure-repl.intellij.parser
  (:require
   [rewrite-clj.zip :as z]))

(defn find-by-heritability
  "Find the leftmost deepest zloc from `start-zloc` that satisfies `inherits?`.
  `inherits?` must be a function such that if zloc satisifies it then so will
  all of its ancestors.

  If a parent node satisifies `inherits?` but none of its children do, then this
  returns the parent, on the assumption that the parent is the last in its
  lineage with the trait.

  If a parent node doesn't satisfy `inherits?` then none of its descendants will
  be inspected. Instead, the search will continue with its sibling to the
  z/right*. As such, this algoritihm can be much faster than ones based on
  z/next*, which must inspect all descendants, even if information in the parent
  excludes the entire family."
  [start-zloc inherits?]
  (loop [zloc (cond-> start-zloc
                (= :forms (z/tag start-zloc)) z/down*)]
    (if (z/end? zloc)
      zloc
      (if (inherits? zloc)
        (if-let [inner (some-> zloc z/down* (z/find z/right* inherits?))]
          (recur inner)
          zloc)
        (recur (z/right* zloc))))))

;; From rewrite-cljs; very similar to the private function
;; rewrite-clj.zip.findz/position-in-range? but based on zloc meta, avoiding the
;; need for :track-position?
(defn in-range?
  "True if b is contained within a."
  [{:keys [row col end-row end-col] :as _a}
   {r :row c :col er :end-row ec :end-col :as _b}]
  (and (>= r row)
       (<= er end-row)
       (if (= r row) (>= c col) true)
       (if (= er end-row) (< ec end-col) true)))

(defn ^:private zloc-in-range?
  "Checks whether the `loc`s node is [[in-range?]] of the given `pos`."
  [loc pos]
  (some-> loc z/node meta (in-range? pos)))

(defn find-form-at-pos
  "Find the deepest zloc whose node is at the given `row` and `col`, seeking
  from initial zipper location `zloc`.

  This is similar to z/find-last-by-pos, but is faster, and doesn't require
  {:track-position? true}."
  [zloc row col]
  (let [exact-position {:row row, :col col, :end-row row, :end-col col}]
    (find-by-heritability zloc #(zloc-in-range? % exact-position))))

(defn root? [loc]
  (identical? :forms (z/tag loc)))

(defn top? [loc]
  (root? (z/up loc)))

(defn to-top
  "Returns the loc for the top-level form above the loc, or the loc itself if it
  is top-level, or nil if the loc is at the `:forms` node."
  [loc]
  (z/find loc z/up top?))

(defn var-name-loc-from-op [loc]
  (cond
    (not loc)
    nil

    (= :map (-> loc z/next z/tag))
    (-> loc z/next z/right)

    (and (= :meta (-> loc z/next z/tag))
         (= :map (-> loc z/next z/next z/tag)))
    (-> loc z/next z/down z/rightmost)

    (= :meta (-> loc z/next z/tag))
    (-> loc z/next z/next z/next)

    :else
    (z/next loc)))

(defn find-var-at-pos
  [root-zloc row col]
  (let [loc (find-form-at-pos root-zloc row col)]
    (some-> loc to-top z/next var-name-loc-from-op)))

(defn to-root
  "Returns the loc of the root `:forms` node."
  [loc]
  (z/find loc z/up root?))

(defn find-namespace [zloc]
  (-> (to-root zloc)
      (z/find-value z/next 'ns)
      z/next))
