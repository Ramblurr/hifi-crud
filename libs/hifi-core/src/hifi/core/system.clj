(ns hifi.core.system
  (:require
   [donut.system :as ds]
   [donut.system.plugin :as dsp]
   [hifi.anomalies :as anom]
   [hifi.core :as h]
   [hifi.error.iface :as he]
   [malli.core :as m]
   [medley.core :as medley]
   [sys-ext.core :as se]))

(defn get-config [system path]
  (get-in system (into [::ds/defs :config] path)))

(defn get-component [system comp-id]
  (get-in system (into [::ds/defs] comp-id)))

(defn component-ids [system]
  (let [component-facet (::ds/defs system)]
    (for [k1 (keys component-facet)
          k2 (keys (get component-facet k1))
          :when (not= :config k1)]
      [k1 k2])))

(defn- coerce-config! [comp-id config-spec supplied-config extra]
  (if config-spec
    (try
      (he/coerce! config-spec (or supplied-config {})
                  {:error-msg    (str "Component '" comp-id "' has invalid config")
                   :more-ex-data (merge extra {::he/id         ::invalid-component-config
                                               ::he/url        (he/url ::invalid-component-config)
                                               :config-value   supplied-config
                                               :component-path comp-id})})
      (catch Exception e
        (if (he/id? e ::invalid-component-config)
          e
          (throw e))))

    supplied-config))

(defn- set-config [component config]
  (cond
    (he/error? config)  config
    (nil? config)       component
    (vector? component) component
    (map? config)       (update component ::ds/config medley/deep-merge config)
    :else               (assoc component ::ds/config config)))

(defn- configure-component [system comp-id]
  (let [component   (get-component system comp-id)
        config-key  (:hifi/config-key component)
        config-spec (:hifi/config-spec component)
        config-path (if (keyword? config-key) [config-key] config-key)
        comp-config (get-config system config-path)]
    (if (and config-spec config-key)
      (->> (coerce-config! comp-id config-spec comp-config {:config-key config-key :config-spec-name (some-> config-spec (m/properties) :name)})
           (set-config component))
      component)))

(defn- aggregate-errors-and-maybe-throw [orig-system final-system]
  (let [errors (->> (ds/component-ids final-system)
                    (map (fn [comp-id]
                           (let [c (get-component final-system comp-id)]
                             (when (he/error? c) (ex-data c)))))
                    (remove nil?))]
    (if (seq errors)
      (throw (ex-info "some hifi system components are incorrectly configured"
                      {::anom/category ::anom/incorrect
                       ::he/id         ::invalid-component-config
                       :errors         errors
                       :before         orig-system
                       :after          final-system}))
      final-system)))

(defn configure-components [system]
  (->> (component-ids system)
       (reduce (fn [system comp-id]
                 (assoc-in system (into [::ds/defs] comp-id) (configure-component system comp-id)))
               system)
       (aggregate-errors-and-maybe-throw system)))

(h/defplugin* hifi-config-plugin
  "Configures and validates components from the config.edn"
  {h/system-update configure-components})

(defn configure-target [system comp-id]
  (let [component        (get-component system comp-id)
        ;; after-target  (:hifi/after component)
        wanted-by-target (:hifi/wanted-by component :hifi.target/default)
        after-targets    (get-in system [::ds/defs :hifi/targets wanted-by-target :hifi/after-targets])]
    (cond-> system
      wanted-by-target    (assoc-in [::ds/defs :hifi/targets wanted-by-target ::ds/config comp-id] [::ds/ref comp-id])
      (seq after-targets) (update-in  (into [::ds/defs] comp-id)
                                      (fn [comp]
                                        (set-config comp
                                                    (reduce
                                                     #(assoc %1 %2 [::ds/ref [:hifi/targets %2]])
                                                     {}
                                                     after-targets)))))))

(defn configure-targets [system]
  (let [system' (->> (component-ids system)
                     (remove #(= :hifi/targets (first %)))
                     (reduce configure-target system))]
    system'))

(h/defplugin* hifi-targets-plugin
  "Configure hifi 'targets' for coarse-grained startup ordering.

   This plugin introduces a simple, systemd-inspired notion of targets (phases)
   Components can declare which target they \"belong\" to via
   `:hifi/wanted-by`, and targets can declare which other targets they must
   start after via `:hifi/after-targets`.

   At system build time this plugin:

   - Registers each component under its `:hifi/wanted-by` target.
   - Adds dependencies from components to any targets listed in that
     target's `:hifi/after-targets`.

   The result is a coarse start-up phasing model (e.g. \"early\" before
   \"default\") without re-implementing systemd's full feature set. We only
   use donut.system's existing dependency graph and leave fine-grained
   ordering to normal component refs."
  {h/system-update configure-targets})

(defn print-progress
  [{::ds/keys [system]}]
  (println "started" (::ds/component-id system)))

(def early-target
  {:doc
   "Target for components that must start in the earliest phase of the
   system, before most other application components.

   Typical examples are logging, telemetry, or other foundational
   infrastructure that you want available as soon as possible so that
   later phases can rely on it implicitly (for example, to emit logs during their
   own startup). Components can opt into this phase by setting
   `:hifi/wanted-by :hifi.target/early`.

   In nearly all cases you should use explicit component references instead of
   using the target system"
   ::ds/start (fn [_])
   ::ds/config {}
   :hifi/after-targets #{}})

(def default-target
  {:doc
   "Default application target and main startup phase.

   Any component that does not explicitly set `:hifi/wanted-by` is treated
   as part of this target. `:hifi.target/default` declares
   `:hifi/after-targets #{:hifi.target/early}`, which means all components
   in the default phase will be started only after the `:hifi.target/early`
   target (and its components) have started.

   This gives a coarse, phase-based startup order (early → default) while
   still relying on donut.system's normal dependency graph for finer
   ordering between individual components."
   ::ds/start (fn [_])
   ::ds/config {}
   :hifi/after-targets #{:hifi.target/early}})

;; TODO find a better name for this funciton
(defn apply-plugins
  ;;notes  Given config map and vector of plugins, build a donut.system defs map
  ;; this uses the donut.system apply-plugins mechanism. in vanilla donut.system, applying plugins
  ;; takes place as part of system start. but we want to "expand" our initial system map, which probably consists
  ;; of very little config (mostly overrides) and lots of plugin defintions, into a fully fleshed out system map with all the defaults
  ;; expanded
  ;; (we really need a small state diagram to show how we go from edn -> expanded config -> start -> running system
  "
  TODO docstring
  "
  [config initial-plugins]
  (let [plugins (conj initial-plugins hifi-config-plugin hifi-targets-plugin)]
    (run! h/validate-plugin plugins)
    (dsp/apply-plugins
     {::ds/defs    {:config config
                    :hifi/targets {:hifi.target/early early-target
                                   :hifi.target/default default-target}}
      ;; ::ds/base #::ds{:post-start print-progress}
      ::ds/plugins plugins})))

(defn resolve-plugins [plugins]
  (mapv (fn [?plugin]
          (try
            (cond
              (symbol? ?plugin)            @(requiring-resolve ?plugin)
              (qualified-keyword? ?plugin) @(requiring-resolve (symbol (namespace ?plugin) (name ?plugin)))
              :else                        ?plugin)
            (catch java.io.FileNotFoundException e
              (throw (ex-info (str "Failed to load plugin" ?plugin) {:plugin ?plugin} e)))))

        plugins))

(defn build-system [config]
  (try
    (dissoc
     (->> (or (:hifi/plugins config) [])
          (resolve-plugins)
          (apply-plugins config)
          (se/remove-dead-refs))
     :donut.system/plugins)
    (catch Exception e
      (throw (ex-info "Building the system failed" {} e)))))
