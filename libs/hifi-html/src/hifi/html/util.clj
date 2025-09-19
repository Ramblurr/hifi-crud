(ns hifi.html.util)

(defn tree-seq-bfs
  "Returns a lazy sequence of nodes in breadth-first order starting from root.

  This is the breadth-first counterpart to clojure.core/tree-seq, which performs
  depth-first traversal. Like tree-seq, the sequence always begins with the root
  node itself, followed by its children level by level.

  Parameters:
    branch?  - A predicate function that returns true for nodes that can have children
    children - A function that returns the children of a branch node
    root     - The root node to start traversal from

  Returns a lazy sequence where nodes at the same depth appear before any nodes
  at greater depth. Within each level, nodes appear in the order returned by
  the children function.

  Examples:
    ;; Simple tree traversal
    (tree-seq-bfs sequential? seq [:a [:b :c] [:d [:e :f]]])
    ;; => ([:a [:b :c] [:d [:e :f]]] :a [:b :c] [:d [:e :f]] :b :c :d [:e :f] :e :f)

    ;; File system traversal (breadth-first)
    (tree-seq-bfs #(.isDirectory %) #(.listFiles %) (io/file \"/some/path\"))

    ;; Empty collection returns sequence containing just the empty collection
    (tree-seq-bfs sequential? seq [])
    ;; => ([])

  Performance: The function maintains a queue internally to achieve breadth-first
  ordering, which may use more memory than depth-first traversal for very deep trees."
  [branch? children root]
  (let [walk (fn walk [queue]
               (lazy-seq
                (when (seq queue)
                  (let [node (first queue)
                        queue' (rest queue)]
                    (cons node
                          (walk (concat queue'
                                        (when (branch? node)
                                          (children node)))))))))]
    (walk [root])))

(defn hiccup-seq
  "Returns a lazy sequence of all hiccup element vectors via breadth-first traversal.

  This function walks through a hiccup data structure and returns only the vector
  elements (not attribute maps, text nodes, or other content). It uses breadth-first
  traversal, meaning parent elements are visited before their children, and siblings
  at the same depth are visited before moving to the next level.

  The traversal intelligently handles hiccup's structure:
  - Recognizes attribute maps (when second element is a map) and skips them
  - Processes both individual hiccup vectors and sequences of hiccup
  - Filters out non-vector content (strings, numbers, nils, etc.)

  Parameter:
    hiccup - A hiccup data structure (vector or sequence of vectors)

  Returns a lazy sequence of hiccup element vectors in breadth-first order,
  starting with the root element if it's a vector.

  Examples:
    ;; Simple hiccup structure
    (hiccup-seq [:div {:class \"container\"} [:h1 \"Title\"] [:p \"Text\"]])
    ;; => ([:div {:class \"container\"} [:h1 \"Title\"] [:p \"Text\"]]
    ;;     [:h1 \"Title\"]
    ;;     [:p \"Text\"])

    ;; Nested structure - note breadth-first ordering
    (hiccup-seq [:html [:head [:title \"Page\"]] [:body [:div [:p \"Text\"]]]])
    ;; => ([:html [:head [:title \"Page\"]] [:body [:div [:p \"Text\"]]]]
    ;;     [:head [:title \"Page\"]]
    ;;     [:body [:div [:p \"Text\"]]]
    ;;     [:title \"Page\"]
    ;;     [:div [:p \"Text\"]]
    ;;     [:p \"Text\"])

    ;; List of elements
    (hiccup-seq '([:div \"one\"] [:div \"two\"]))
    ;; => ([:div \"one\"] [:div \"two\"])

    ;; Empty collection returns sequence containing the empty vector
    (hiccup-seq [])
    ;; => ([])

  Use cases:
    - Finding or transforming specific elements in hiccup
    - Analyzing hiccup structure
    - Extracting all elements of a certain type
    - Validating hiccup structure"
  [hiccup]
  (sequence
   (filter vector?)
   (tree-seq-bfs
    #(or (vector? %) (sequential? %))
    (fn [node]
      (cond
        (vector? node)
        (let [has-attrs? (map? (second node))
              children-start (if has-attrs? 2 1)]
          (drop children-start node))
        (sequential? node) (seq node)
        :else nil))
    hiccup)))

(defn find-first-element
  "Returns the first hiccup element (vector) that satisfies the predicate function,
  or nil if no matching element is found. Uses breadth-first traversal.

  The predicate function is only called with hiccup element vectors, never with
  attributes maps, text nodes, or other non-element content.

  Examples:
    (find-first-element #(= :head (first %)) [:html [:head {} [:title \"Page Title\"]]])
    ;; => [:head {} [:title \"Page Title\"]]"
  [pred hiccup]
  (transduce
   (filter pred)
   (fn
     ([] nil)
     ([result] result)
     ([_ x] (reduced x)))
   nil
   (hiccup-seq hiccup)))
