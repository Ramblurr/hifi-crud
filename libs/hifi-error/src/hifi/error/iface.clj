;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.error.iface
  (:require
   [hifi.anomalies :as anom]
   [clojure.string :as str]
   [malli.transform :as mt]
   [malli.core :as m]
   [malli.error :as me]))

;; Inspired by Daniel's library donut-party/error https://github.com/donut-party/error

(defn url
  [error-id]
  (str "https://hifi/errors/#" (str/replace (str error-id) #"/" "_")))

(def ^:private default-transformer (mt/default-value-transformer {::mt/add-optional-keys true}))

(defn throw-explanation [_opts error-msg more-ex-data {:keys [_value explain]}]
  (let [explain (-> explain (me/with-error-messages))
        ei      (ex-info (or error-msg
                             (some-> more-ex-data ::id str)
                             (str ::schema-validation-error))
                         (merge
                          {::anom/category    ::anom/incorrect
                           ::id               ::schema-validation-error
                           ::url              (url ::schema-validation-error)
                           :explanation-human {:schema   (or (-> explain :schema m/properties :name) "schema is missing :name")
                                               :problems (-> explain
                                                             (me/with-spell-checking)
                                                             (me/humanize {:resolve me/-resolve-root-error}))}
                           :explanation       explain}
                          more-ex-data))]
    (tap> ei)
    (throw ei)))

(defn coercer
  "Creates a function to decode and validate a value, throws on validation error.

  `:error-msg` - Error message to use in thrown exception
  `:more-ex-data` - Additional ex-data to merge into thrown exception
  `:transformer` - Malli transformer to use for decoding, by default uses the default-value transformer with add-optional-keys enabled
  All other options are passed directly to malli.
  "
  [schema {:keys [error-msg more-ex-data transformer] :or {transformer default-transformer} :as opts}]
  (try
    (m/coercer (m/schema schema) transformer identity
               (partial throw-explanation opts error-msg more-ex-data)
               (dissoc opts :error-msg :more-ex-data :transformer))
    (catch clojure.lang.ExceptionInfo e
      (if (= :malli.core/invalid-schema (:type  (ex-data e)))
        (throw (ex-info "Malli schema definition is invalid"
                        {::anom/category ::anom/incorrect
                         ::id ::invalid-schema
                         ::url (url ::invalid-schema)
                         :schema schema}))

        (throw e)))))

(defn coerce!
  "Decode `value` with `schema` and throw exception if value is invalid.
  Optionally takes a third argument opts map that may contain:

  `:error-msg` - Error message to use in thrown exception
  `:more-ex-data` - Additional ex-data to merge into thrown exception
  `:transformer` - Malli transformer to use for decoding, by default uses the default-value transformer with add-optional-keys enabled

  All other options are passed directly to malli.

  Use `coercer` if performance is important.
  "
  ([schema value]
   (coerce! schema value nil))
  ([schema value opts]
   ((coercer schema opts) value)))

(defn validator!
  "Returns a validation function that validates a value, or throws an exception.

  Options can contain:

  `:error-msg` - Error message to use in thrown exception
  `:more-ex-data` - Additional ex-data to merge into thrown exception

  All other options are passed directly to malli.
  "
  [schema {:keys [error-msg more-ex-data] :as opts}]
  (let [malli-opts (dissoc opts :error-msg :more-ex-data)
        validator  (m/validator schema malli-opts)
        explainer  (m/explainer schema malli-opts)]
    (fn _validator! [value]
      (when-not (validator value)
        (throw-explanation opts error-msg more-ex-data {:value value :schema schema :explain (explainer value)})))))

(defn validate!
  "Validate `value` against `schema` and throw exception if value is invalid.
  Optionally takes a third argument opts map that may contain:

  `:error-msg` - Error message to use in thrown exception
  `:more-ex-data` - Additional ex-data to merge into thrown exception

  All other options are passed directly to malli.

  If performance is essential, use validator!
  "
  ([schema value]
   (validate! schema value nil))
  ([schema value opts]
   ((validator! schema opts) value)))

(defn error?
  "Returns true if `ex` is an ex-info error created by `hifi.error.iface`"
  [ex]
  (-> ex (ex-data) ::id keyword?))

(defn id?
  "Returns true if the exception `ex` is an ex-info error with the given `:hifi.error.iface/id`."
  [ex id]
  (= id (-> ex (ex-data) ::id)))

(defn bling-schema-error [error]
  (when (id? error ::schema-validation-error)
    (let [{:as _data :keys [explanation]} (ex-data error)
          value                          (-> explanation :value)
          schema                         (-> explanation :schema)]
      ((requiring-resolve 'bling.explain/explain-malli)
       schema value))))
