(ns ^:no-doc hifi.core.impl
  (:require
   [clojure.java.io :as io]
   [clojure.tools.reader :as tools.reader]
   [clojure.tools.reader.reader-types :as reader-types]))

(defn provider-decls
  "Return a seq of {:sym 'name :kind :fn|:macro :meta <meta>}"
  [prov-ns]
  (for [[sym v] (ns-interns prov-ns)
        :let [m (meta v)]
        :when (or (:hifi.core/callback m) (:hifi.core/macro-callback m))]
    {:sym sym
     :kind (if (:hifi.core/macro-callback m) :macro :fn)
     :meta m}))

(defn parse-extend-spec
  "Accepts:
     '[ns.sym :opts {:k v}]    (usual quoted form)
      [ns.sym :opts {:k v}]    (unquoted vector)
   Returns [ns-sym opts-map]."
  [spec]
  (let [v (cond
            ;; quoted vector (most common, like (require '[...]))
            (and (seq? spec) (= 'quote (first spec))) (second spec)
            ;; direct vector
            (vector? spec) spec
            :else (throw (ex-info "extend-ns expects a vector like '[ns :opts {...}]"
                                  {:got spec})))
        prov-ns (first v)
        ;; look for :opts <map> pair
        opts    (or (some (fn [[k val]] (when (= k :opts) val))
                          (partition 2 2 nil (rest v)))
                    {})]
    (when-not (symbol? prov-ns)
      (throw (ex-info "First element of extend spec must be a symbol (namespace)."
                      {:got prov-ns :spec v})))
    (when-not (or (map? opts) (nil? opts))
      (throw (ex-info ":opts value must be a map." {:opts opts :spec v})))
    [prov-ns (or opts {})]))

(defn source->resource [source-file]
  (when source-file
    (let [file (io/file source-file)]
      (cond
        (.exists file) file
        :else (io/resource source-file)))))
(defn read-defroutes-form [rdr sym target-line]
  (let [pushback (reader-types/indexing-push-back-reader rdr)
        opts {:eof ::eof :read-cond :preserve :features #{:clj}}]
    (loop []
      (let [form (tools.reader/read opts pushback)]
        (cond
          (= ::eof form) nil
          (not (sequential? form)) (recur)
          :else
          (let [[op binding-name & body] form
                form-meta (meta form)
                op-name (some-> op name)]
            (if (and (= "defroutes" op-name)
                     (= sym binding-name)
                     (or (nil? target-line)
                         (= target-line (:line form-meta))))
              (let [body (if (string? (first body)) (rest body) body)]
                (first body))
              (recur))))))))
(defn enrich-route-form [sym default-form source-meta]
  (try
    (let [{:keys [file line]} source-meta
          resource (source->resource file)]
      (if resource
        (with-open [r (io/reader resource)]
          (or (read-defroutes-form r sym line)
              default-form))
        default-form))
    (catch Exception _
      default-form)))

(defn route-form-line [form]
  (or (:line (meta form))
      (some-> form first meta :line)))

(defn annotate-route-form [dev? sym current-ns fallback-line form]
  (when-not (vector? form)
    (throw (ex-info "Route entries must be vectors"
                    {:hifi/error ::invalid-route-entry
                     :symbol sym
                     :entry form})))
  (let [line          (or (route-form-line form) fallback-line)
        annotation    `(when ~dev?
                         {:ns '~current-ns
                          :line ~line})
        [path & rest*] form
        has-map?      (and (seq rest*) (map? (first rest*)))
        route-map     (when has-map? (first rest*))
        child-forms   (if has-map?
                        (rest rest*)
                        rest*)
        annotated     (map (partial annotate-route-form dev? sym current-ns line) child-forms)]
    (if has-map?
      `(let [path# ~path
             data# ~route-map
             annotation# ~annotation
             data'# (if (and annotation# (not (contains? data# :hifi/annotation)))
                      (assoc data# :hifi/annotation annotation#)
                      data#)]
         (into [path# data'#]
               [~@annotated]))
      `(let [path# ~path
             annotation# ~annotation]
         (into (cond-> [path#]
                 annotation# (conj {:hifi/annotation annotation#}))
               [~@annotated])))))
