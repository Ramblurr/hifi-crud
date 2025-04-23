;; MIT License
;; part of clojure-plus https://github.com/tonsky/clojure-plus/blob/4db2bb081045d19034a93b33fd513c51cc4cddcc/src/clojure%2B/error.clj
;; Copyright (c) 2025 Nikita Prokopov
;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:

;; The above copyright notice and this permission notice shall be included in all
;; copies or substantial portions of the Software.

;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.
(ns app.util.error
  (:require
   [medley.core :as medley]
   [clojure.string :as str])
  (:import
   [clojure.lang Compiler]
   [java.io Writer]))

(def ignored-cls-re
  (re-pattern
   (str "^("
        (str/join "|"
                  ["clojure.lang"
                   "clojure.main"
                   "clojure.core.server"
                   "clojure.core/eval"
                   "clojure.core/binding-conveyor-fn"
                   "java.util.concurrent.FutureTask"
                   "java.util.concurrent.ThreadPoolExecutor"
                   "java.util.concurrent.ThreadPoolExecutor/Worker"
                   "java.lang.VirtualThread"
                   "java.lang.Thread"])
        ").*")))

(def remove-ignored-cls-xf
  ;; We don't care about var indirection
  (remove (fn [{:keys [cls]}] (re-find ignored-cls-re cls))))

(defn trace-element [^StackTraceElement el]
  (let [file     (.getFileName el)
        line     (.getLineNumber el)
        cls      (.getClassName el)
        method   (.getMethodName el)
        clojure? (if file
                   (or (.endsWith file ".clj") (.endsWith file ".cljc") (= file "NO_SOURCE_FILE"))
                   (#{"invoke" "doInvoke" "invokePrim" "invokeStatic"} method))

        [ns separator method]
        (cond
          (not true #_(:clean? config))
          [cls "." method]

          (not clojure?)
          [(-> cls (str/split #"\.") last) "." method]

          (#{"invoke" "doInvoke" "invokeStatic"} method)
          (let [[ns method] (str/split (Compiler/demunge cls) #"/" 2)
                method      (-> method
                                (str/replace #"eval\d{3,}" "eval")
                                (str/replace #"--\d{3,}" ""))]
            [ns "/" method])

          :else
          [(Compiler/demunge cls) "/" (Compiler/demunge method)])]
    {:element   el
     :file      (if (= "NO_SOURCE_FILE" file) nil file)
     :line      line
     :cls       cls
     :ns        ns
     :separator separator
     :method    method}))

(let [v {:ns "wtf.omg.cool"}]
  (first (filter #(str/starts-with? (:ns v) (str %)) #{'wtf})))

(let [v {:ns "wtf.omg.cool"}]
  (first (filter #(str/starts-with? (:ns v) (str %)) #{'wtf})))

(defn compact-trace-xf
  "Dedupes consecutive stack trace elements whose :ns are a prefix of the ns in ns-set"
  [ns-set]
  (medley/dedupe-by (fn [v]
                      (let [matches (filter #(str/starts-with? (:ns v) (str %)) ns-set)]
                        (if (empty? matches)
                          v
                          matches)))))

(defn clean-trace
  ([trace]
   (clean-trace nil trace))
  ([opts trace]
   (into []
         (comp
          (map trace-element)
          remove-ignored-cls-xf
          (medley/dedupe-by (juxt :ns :method))
          (compact-trace-xf (:compact opts)))
         trace)))

(defmacro write [w & args]
  (list* 'do
         (for [arg args]
           (if (or (string? arg) (= String (:tag (meta arg))))
             `(Writer/.write ~w ~arg)
             `(Writer/.write ~w (str ~arg))))))

(defn- pad [ch ^long len]
  (when (pos? len)
    (let [sb (StringBuilder. len)]
      (.repeat sb (int ch) len)
      (str sb))))

(defn- longest-method [trace]
  (reduce max 0
          (for [el trace]
            (+  (count (:ns el)) (count (:separator el)) (count (:method el))))))

(defn print-trace [trace w]
  (let [depth   0
        max-len (longest-method trace)
        indent  (pad \space depth)]
    (loop [trace  trace
           first? false]
      (when-not (empty? trace)
        (let [el                                      (first trace)
              {:keys [ns separator method file line]} el
              right-pad                               (pad \space (- max-len depth (count ns) (count separator) (count method)))]
          (when-not first?
            (write w "\n" indent "  "))
          (write w "[" ns separator method)
          (cond
            (= -2 line)
            (write w right-pad "  :native-method")

            file
            (do
              (write w right-pad "  ")
              (print-method file w)
              (write w " " line)))
          (write w "]")
          (recur (next trace) false))))))
