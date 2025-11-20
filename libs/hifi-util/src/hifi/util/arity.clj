(ns hifi.util.arity)

(defn arity
  "Returns the arities (a vector of ints) of:
    - anonymous functions like `#()` and `(fn [])`.
    - defined functions like `map` or `+`.
    - macros, by passing a var like `#'->`.

  Returns `[:variadic]` if the function/macro is variadic.
  Otherwise returns nil"
  [f]
  (let [func      (if (var? f) @f f)
        methods   (->> func
                       class
                       .getDeclaredMethods
                       (map (fn [^java.lang.reflect.Method m]
                              (vector (.getName m)
                                      (count (.getParameterTypes m))))))
        var-args? (some #(-> % first #{"getRequiredArity"})
                        methods)
        arities   (->> methods
                       (filter (comp #{"invoke"} first))
                       (map second)
                       (sort))]
    (cond
      (keyword? f)     nil
      var-args?        [:variadic]
      (empty? arities) nil
      :else            (if (and (var? f) (-> f meta :macro))
                         (mapv #(- % 2) arities) ;; substract implicit &form and &env arguments
                         (into [] arities)))))

(defn zero-arity? [f]
  (= 0 (first (arity f))))

(def ZeroArityFn [:fn {:error/message "should be a 0-arity function"} zero-arity?])
