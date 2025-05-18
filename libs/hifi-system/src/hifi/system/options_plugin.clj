;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.system.options-plugin
  (:import
   [clojure.lang ExceptionInfo])
  (:require
   [hifi.anomalies.iface :as anom]
   [hifi.error.iface :as de]
   [donut.system :as ds]
   [donut.system.plugin :as dsp]))

(defn- get-in-defs [system ks]
  (get-in system (into [::ds/defs] ks)))

(defn- coerce-config! [comp-id options-schema supplied-options]
  (de/coerce! options-schema (or supplied-options {})
              {:error-msg    (str "Component '" comp-id "' has invalid options")
               :more-ex-data {::de/id         ::invalid-component-options
                              ::de/url        (de/url ::invalid-component-options)
                              :component-path comp-id
                              :schema-path    (into [::ds/defs] (conj comp-id :hifi/options-schema))
                              :options-path   (into [::ds/defs] (conj comp-id :hifi/options-ref))}}))

(defn- configure-component-defaults [sys comp-id {:keys [:hifi/options-schema :hifi/options-ref] :as c}]
  (try
    (assert options-ref)
    (assert options-schema)
    (let [supplied-options (get-in-defs sys options-ref)
          options-munge    (or (:hifi/options-munge c) (fn [_ opt] opt))
          final-options    (->> supplied-options
                                (coerce-config! comp-id options-schema)
                                (options-munge sys))]
      (assoc-in c [::ds/config :hifi/options] final-options))
    (catch ExceptionInfo e
      (if (de/id? e ::invalid-component-options)
        e
        (throw e)))))

(defn- maybe-update-component-config [system comp-id]
  (let [c (get-in-defs system comp-id)]
    (if (and (:hifi/options-schema c) (:hifi/options-ref c))
      (assoc-in system (into [::ds/defs] comp-id) (configure-component-defaults system comp-id c))
      system)))

(defn- aggregate-errors-and-maybe-throw [system]
  (let [errors (->> (ds/component-ids system)
                    (map (fn [comp-id]
                           (let [c (get-in-defs system comp-id)]
                             (when (de/error? c)
                               (ex-data c)))))
                    (remove nil?))]
    (if (seq errors)
      (throw (ex-info "Hifi system components failed validation"
                      {::anom/category ::anom/incorrect
                       ::de/id         ::invalid-component-options-aggregate
                       :errors         errors}))
      system)))

(defn- system-merge-default-options [system]
  (->> (ds/component-ids system)
       (reduce maybe-update-component-config system)
       (aggregate-errors-and-maybe-throw)))

(def options-plugin
  #::dsp{:name
         ::options-plugin

         :doc
         "Hifi's donut.party/system options plugin.

This plugin is responsible for merging user-supplied options and
default options into the system. It does this by looking at each
component in the system defs and checking if it has a
`:hifi/options-schema` and  a `:hifi/options-ref`. If it does,
it will merge the user-options at options-ref into the config for the component under the :hifi/options key.

In your donut.system component definitions you can specify the following extra keys:

  - `:hifi/options-schema` is a malli schema that describes the shape of the options
     the component expects to receive. This schema should be open, malli's default,
     to allow for the unexpected. When distributing components in the hifi ecosystem,
     the component author should supply a well-formed options schema, complete with
     documentation.

  - `:hifi/options-ref` is a system reference, like `:donut.system/ref` to another path
     in the system map where the user-supplied options can be found. 99.9% of the time
     this should be a path under `:env`, where hifi places the contents of `env.edn`.

  - `:hifi/options-munge` is a an optional function that takes the system and
     coerced options and returns new options

The plugin will report errors when an invalid option map is encountered.

The resulting coerced (i.e, default values added) and validated options map will be placed under
`::ds/config :hifi/options`, so they are available for the start and stop
handlers. "

         :system-update system-merge-default-options})
