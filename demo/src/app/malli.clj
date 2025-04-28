;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT


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

(comment
  (defn properties-for [schema k]
    (second
     (medley/find-first #(= k (first %)) (m/children schema))))

  ;; get the keys
  (mu/keys a-schema)
  ;; => (:btn/size :btn/priority :btn/disabled? :btn/loading? :btn/icon :btn/icon-trailing)

  ;; get schema top level properties
  (m/properties a-schema)
  ;; => {:name "Button", :key :app.ui/button}

  ;; get a child type value
  (m/children (mu/get-in a-schema [:btn/size]))
  ;; => [:xxsmall :xsmall :small :normal :large]

  ;; get the type of a child
  (m/type (mu/get-in a-schema [:btn/size]))
  ;; => :enum

  ;; get the properties of a child
  (properties-for a-schema :btn/size)
  ;; => {:optional true, :doc "Button size"}
  )
