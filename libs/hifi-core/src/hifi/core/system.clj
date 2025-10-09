(ns hifi.core.system
  (:require
   [donut.system :as ds]
   [donut.system.plugin :as dsp]
   [hifi.anomalies :as anom]
   [hifi.core :as h]
   [hifi.error.iface :as he]
   [malli.core :as m]
   [medley.core :as medley]))

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
    (he/error? config) config
    (nil? config)      component
    (map? config)      (update component ::ds/config medley/deep-merge config)
    :else              (assoc component ::ds/config config)))

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

;; TODO find a better name for this funciton
(defn build-system
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
  (let [plugins (conj initial-plugins hifi-config-plugin)]
    (run! h/validate-plugin plugins)
    (dsp/apply-plugins
     {::ds/defs    {:config config}
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
