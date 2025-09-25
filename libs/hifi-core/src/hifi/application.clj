(ns hifi.application
  (:require
   [donut.system :as ds]
   [donut.system.plugin :as dsp]
   [hifi.anomalies.iface :as anom]
   [hifi.application.finisher :as finisher]
   [hifi.core :as h :refer [defcallback defplugin* deftemplate]]
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
    (map? config)
    (update component ::ds/config medley/deep-merge config)
    :else
    (assoc component ::ds/config config)))

(defn- configure-component [system comp-id]
  (let [component   (get-component system comp-id)
        config-key  (:hifi/config-key component)
        config-spec (:hifi/config-spec component)
        config-path (if (keyword? config-key) [config-key] config-key)]
    (if (and config-spec config-key)
      (set-config component (coerce-config! comp-id config-spec (get-config system config-path) {:config-key config-key :config-spec-name (some-> config-spec (m/properties) :name)}))
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

(defplugin* hifi-config-plugin
  "Configures and validates components from the config.edn"
  {h/system-update configure-components})

(defcallback plugins "The system plugins to load" ([] [=> :vector]))
(defcallback initialize "Build the system map" ([] [=> :map]))
(defcallback config "Load configuration" ([] [=> :map]))
(defcallback start "Start the application" ([] [=> :map]))
(defcallback stop "Stop the application" ([] [=> :map]))

(defn build-system
  "Given config map and vector of plugins, build a donut.system defs map"
  [config initial-plugins]
  (let [plugins (conj initial-plugins finisher/hifi-finisher-plugin hifi-config-plugin)]
    (run! h/validate-plugin plugins)
    (dsp/apply-plugins
     {::ds/defs    {:config config}
      ::ds/plugins plugins})))

(deftemplate
  (require '[donut.system])
  (require '[hifi.core])
  (require '[hifi.config])
  (require '[hifi.application])

  (defonce running-system_ (atom nil))

  (hifi.core/defn-default ::marker config
    "Load configuration"
    []
    (hifi.config/read-config))

  (hifi.core/defn-default ::marker initialize []
    (hifi.application/build-system (config) plugins))

  (hifi.core/defn-default ::marker start
    "Start the application"
    []
    (let [i (initialize)
          s (donut.system/start i)]
      (reset! running-system_ s)
      s))

  (hifi.core/defn-default ::marker stop
    "Stop the application"
    []
    (when @running-system_
      (reset! running-system_ (donut.system/stop @running-system_)))))

(comment
  (do
    (remove-ns 'hifi.backtick)
    (remove-ns 'hifi.core)
    (remove-ns 'hifi.application)
    (remove-ns 'hello.application))
  ;; rcf
  )
