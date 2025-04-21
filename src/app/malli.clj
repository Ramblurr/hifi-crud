(ns app.malli
  (:require
   [app.schema.common :as common]
   [malli.core :as m]
   [malli.error :as me]
   [malli.registry :as mr]
   [malli.transform :as mt]))

(def registry
  (mr/composite-registry
   (m/default-schemas)
   common/registry))

(def malli-opts {:registry registry})

(defn schema->map
  "Converts a malli schema into a plain data map"
  [s]
  (m/walk
   s
   (fn [schema _ children _]
     (-> (m/properties schema malli-opts)
         (assoc :malli/type (m/type schema))
         (cond-> (seq children) (assoc :malli/children children))))
   malli-opts))

(defn schema-keys [schema]
  (let [d        (schema->map schema)
        map-data (cond
                   (= :map (:malli/type d))
                   d
                   (#{:and} (:malli/type d))
                   (first (:malli/children d))
                   :else (throw (ex-info "Unexpected schema shape" {:schema schema})))]
    (mapv first (:malli/children map-data))))

(defn schema-name [schema]
  (get (schema->map schema) :name "unknown schema name. it is missing :name"))

(defn valid? [schema value]
  (m/validate schema value malli-opts))

(defn explain [schema value]
  (m/explain schema value malli-opts))

(def humanize me/humanize)
