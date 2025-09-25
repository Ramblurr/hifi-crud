(ns hifi.core
  (:require
   [clojure.java.io :as io]
   [clojure.tools.reader :as tools.reader]
   [clojure.tools.reader.reader-types :as reader-types]
   [donut.system :as ds]
   [donut.system.plugin :as dsp]
   [hifi.config :as config]
   [hifi.backtick :as backtick]
   [hifi.error.iface :as pe]))

(def DonutPlugin
  "Our enhanced donut.system plugin schema, more strict than ds/Plugin"
  [:and {:name :donut.system/plugin}
   [:map
    [::dsp/name {:error/message "plugin names must be namespace qualified"} qualified-keyword?]
    [::ds/doc {:error/message "a docstring is required for plugins"} string?]
    [::dsp/system-defaults {:optional true} ds/PluginSystem]
    [::dsp/system-merge {:optional true} ds/PluginSystem]
    [::dsp/system-update {:optional true} fn?]]
   [:fn {:error/message "At least one of ::dsp/system-defaults, system-merge, or system-update is required"}
    (fn [{::dsp/keys [system-defaults system-merge system-update]}]
      (or system-defaults system-merge system-update))]])

(def plugin-name
  "Key for the plugin's unique identifier in plugin definitions.

  Maps to `:donut.system.plugin/name`. When used in a plugin map, the value
  should be a namespace-qualified keyword that uniquely identifies the plugin.
  This name is used for plugin resolution and debugging purposes."
  ::dsp/name)

(def plugin-doc
  "Key for plugin documentation strings in plugin definitions.

  The value should be a string describing the plugin's purpose and behavior. "
  ::ds/doc)

(def system-defaults
  "Plugin configuration that provides default values for the system.

  When used in a plugin, values under this key are merged into the system with
  lower precedence, allowing users to override plugin defaults. Useful for
  providing sensible defaults for optional configuration while keeping the
  plugin flexible.

  The merge follows recursive merge semantics, see [[defplugin]]. "
  ::dsp/system-defaults)

(def system-merge
  "Plugin configuration that takes precedence over existing system values.

  When used in a plugin, values under this key override corresponding values
  already in the system. Use this when your plugin needs to enforce specific
  configurations or when adding new components that shouldn't be overridden.

  The merge follows recursive merge semantics, see [[defplugin]]."
  ::dsp/system-merge)

(def system-update
  "Function key for custom system transformation logic.

  When used in a plugin, this key should map to a function `(fn [system] ...)`
  that transforms the system map. Use this for complex modifications that can't
  be expressed as simple merges, such as conditional updates, computed values,
  or transformations that depend on existing system state."
  ::dsp/system-update)

(defn validate-plugin [plugin-map]
  (pe/validate! DonutPlugin plugin-map
                {:error-msg    "Plugin definition is invalid"
                 :more-ex-data {::pe/id  ::invalid-plugin-definition
                                ::pe/url (pe/url ::plugin-definition)}}))

(defmacro defplugin*
  "Defines a donut.system plugin with compile-time validation.

   Takes a name symbol, a doc-string, and a plugin-body map containing the
   plugin configuration. Automatically sets `:donut.system.plugin/name` to the
   namespaced keyword derived from `name` and `:donut.system/doc` to the provided
   docstring.

   The `body` should contain at least one of the keys
   `:donut.system.plugin/system-defaults`, `:donut.system.plugin/system-merge`,
   or `:donut.system.plugin/system-update`.

   Validates the resulting plugin map against the schema at macro expansion
  time, throwing an exception if invalid.

   This macro is optional. Plugin definitions are plain data maps and can be
   created manually. This macro provides convenience and compile-time validation.

   Plugin merging uses recursive semantics: when maps are merged, if both maps
   contain nested maps under the same keys, those nested maps are merged rather
   than replaced. Non-map values are overwritten as with `clojure.core/merge`.

   For example, merging `{:a {:b 1}}` with `{:a {:c 2}}` yields `{:a {:b 1 :c 2}}`
   rather than `{:a {:c 2}}`. This allows plugins to extend nested configurations
   without completely replacing them."

  [name doc-string body]
  (let [plugin-name (keyword (str *ns*) (str name))]
    `(def ~name
       (let [plugin-map# (merge {::dsp/name ~plugin-name
                                 ::ds/doc  ~doc-string}
                                ~body)]
         (validate-plugin plugin-map#)
         plugin-map#))))

(defmacro defplugin
  "Convenience macro for defining and validating donut.system plugins with default components.

   Takes a name symbol, a doc-string, and a map of component definitions.
   Automatically wraps the definitions in `:donut.system.plugin/system-defaults`
   and `:donut.system/defs` keys, making it simpler to define plugins that only
   provide default components.

   This is equivalent to calling [[defplugin*]] with:
   `{:donut.system.plugin/system-defaults {:donut.system/defs body}}`

   See [[defplugin*]] for full documentation on plugin behavior and validation."
  [name doc-string body]
  `(defplugin* ~name ~doc-string
     {system-defaults {::ds/defs ~body}}))

(defmacro defn-default
  "Like defn, but only defines the function if it doesn't already exist or re-defs it if the existing markers are equal.
   Differs from defonce because it preserves existing metadata and docstrings if the var is already defined."
  ([marker name & body]
   (when (or (not (resolve name))
             (= marker (-> name (resolve) (meta) ::marker)))
     `(defn ~(with-meta name {::marker marker}) ~@body))))

(def => :ret)
(def | :st)
(def <- :gen)

(defmacro defcallback
  "Required function with multiple arities, each with Guardrails/Malli sig."
  [sym & body]
  (let [[doc arities] (if (string? (first body)) [(first body) (rest body)] [nil body])
        parsed-arities
        (for [[args sig] arities]
          {:args   args
           :sig    sig})
        meta-map
        {::callback true
         ::callback-kind :fn
         ::callback-arities
         ;; store a *vector of literal maps* about each arity
         (vec (for [{:keys [args sig parsed]} parsed-arities]
                {:args   (list 'quote args)
                 :sig    (list 'quote sig)
                 :parsed parsed}))}]
    ;; Again: syntax-quote outer def; do NOT use (quote …) around anything with ~
    `(def ~(with-meta sym meta-map)
       ~(or doc {})
       {::callback true})))

(defmacro defcallback-macro
  "Declare a *required* macro callback. Records arities in var metadata.
     (defmacrocallback span
       \"doc\"
       [arg1 arg2])"
  [sym & body]
  (let [[doc arities] (if (string? (first body))
                        [(first body) (rest body)]
                        [nil body])
        ;; store literal arglists in meta as quoted forms
        arity-lits (vec (for [a arities] (list 'quote a)))
        meta-map   {::macro-callback true
                    ::callback-kind :macro
                    ::callback-arities arity-lits}]
    ;; outer form is syntax-quoted so ~(with-meta …) is OK; meta-map is plain data
    `(def ~(with-meta sym meta-map)
       ~(or doc {})
       {::callback true})))

(defmacro deftemplate
  "Define __extend__ in the provider ns.

  Use ~opts in the body to access params"
  [& body]
  `(do (defmacro ~'__extend__ [& {:as ~'opts}]
         (backtick/template (do ~@body)))))

(defn- provider-decls
  "Return a seq of {:sym 'name :kind :fn|:macro :meta <meta>}"
  [prov-ns]
  (for [[sym v] (ns-interns prov-ns)
        :let [m (meta v)]
        :when (or (::callback m) (::macro-callback m))]
    {:sym sym
     :kind (if (::macro-callback m) :macro :fn)
     :meta m}))

(defn ensure-declared-in-caller
  [prov-ns decls]
  (doseq [{:keys [sym kind]} decls]
    (let [v (ns-resolve *ns* sym)]
      (when (nil? v)
        (throw (ex-info (format "Missing required %s `%s` before (extend-ns %s)."
                                (name kind) sym prov-ns)
                        {:missing sym :kind kind
                         :ns (ns-name *ns*) :provider prov-ns})))
      (case kind
        :macro (when-not (:macro (meta v))
                 (throw (ex-info (format "`%s` must be a macro (required by %s)."
                                         sym prov-ns)
                                 {:bad-kind :not-macro :sym sym})))
        :fn    (when (:macro (meta v))
                 (throw (ex-info (format "`%s` must be a function (required by %s)."
                                         sym prov-ns)
                                 {:bad-kind :is-macro :sym sym})))))))

(defn- parse-extend-spec
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

(defmacro extend-ns
  "Place at the **end** of the file.
   Example:
     (extend-ns hifi.application)
     ;; or with options you might add later
     (extend-ns hifi.application :foo :bar)"
  [spec]
  (let [[prov-ns opts] (parse-extend-spec spec)
        _ (require prov-ns)
        ext-sym        (symbol (str prov-ns) "__extend__")
        decls          (provider-decls prov-ns)]
    (require 'hifi.core)
    `(do
       (~ext-sym ~opts)
       ;; after provider code has defined any defaults, enforce required callbacks
       (hifi.core/ensure-declared-in-caller '~prov-ns '~decls)
       :extended)))

(defn- source->resource [source-file]
  (when source-file
    (let [file (io/file source-file)]
      (cond
        (.exists file) file
        :else (io/resource source-file)))))

(defn- read-defroutes-form [rdr sym target-line]
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

(defn- enrich-route-form [sym default-form source-meta]
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

(defn- route-form-line [form]
  (or (:line (meta form))
      (some-> form first meta :line)))

(defn- annotate-route-form [sym current-ns fallback-line form]
  (when-not (vector? form)
    (throw (ex-info "Route entries must be vectors"
                    {:hifi/error ::invalid-route-entry
                     :symbol sym
                     :entry form})))
  (let [line          (or (route-form-line form) fallback-line)
        annotation    `(when (config/dev?)
                         {:ns '~current-ns
                          :line ~line})
        [path & rest*] form
        has-map?      (and (seq rest*) (map? (first rest*)))
        route-map     (when has-map? (first rest*))
        child-forms   (if has-map?
                        (rest rest*)
                        rest*)
        annotated     (map (partial annotate-route-form sym current-ns line) child-forms)]
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

(defmacro defroutes
  "Define named route data that mirrors reitit's vector syntax.

  Usage is identical to `def`, with an optional docstring followed by a
  literal route vector. The resulting var holds a map with a namespaced
  `:route-name` and the original routes under `:routes`.

  In development profiles the route maps are enriched with `:hifi/annotation`
  entries pointing back to their namespace and source line, helping tooling and
  debug output trace routes to their definitions. Production builds keep the
  route data untouched."
  [sym & body]
  (let [[doc body] (if (string? (first body))
                     [(first body) (rest body)]
                     [nil body])
        route-form (first body)
        source-meta (merge {:file *file*} (meta &form))
        effective-route-form (if (config/dev?) (enrich-route-form sym route-form source-meta) route-form)]
    (when-not route-form
      (throw (ex-info "defroutes requires a route vector"
                      {:hifi/error ::missing-route-vector
                       :symbol sym})))
    (when-not (vector? route-form)
      (throw (ex-info "defroutes expects a literal vector for route data"
                      {:hifi/error ::invalid-route-form
                       :symbol sym
                       :provided route-form})))
    (let [current-ns (ns-name *ns*)
          route-name (keyword (str current-ns) (name sym))
          fallback-line (or (route-form-line effective-route-form)
                            (:line source-meta)
                            0)
          routes-expr (annotate-route-form sym current-ns fallback-line effective-route-form)
          def-form (if doc
                     `(def ~sym ~doc
                        {:route-name ~route-name
                         :routes ~routes-expr})
                     `(def ~sym
                        {:route-name ~route-name
                         :routes ~routes-expr}))]
      def-form)))
