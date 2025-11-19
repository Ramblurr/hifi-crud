;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.datomic.helpers
  (:require [datomic.api :as d]))

(defn- max1
  [query-result]
  (assert (< (count query-result) 2))
  (assert (< (count (first query-result)) 2))
  (ffirst query-result))

;; Get id whether from number or datomic ent
(defprotocol Eid
  "Protocol for getting a Datomic entity ID from different types."
  (e [entity]
    "Returns the entity ID whether from a number or Datomic entity."))

(extend-protocol Eid
  java.lang.Long
  (e [n] n)

  datomic.Entity
  (e [ent] (:db/id ent)))

(defn ent
  "Returns Datomic entity from id, or nil if none exists.

   Example:
   ```
   ;; Get entity with ID 42
   (ent 42 db)

   ;; Returns nil for non-existent entity
   (ent 999 db) ;; => nil
   ```"
  [id db]
  (if-let [exists (max1 (d/q '[:find ?eid
                               :in $ ?eid
                               :where [?eid]]
                             db (e id)))]
    (d/entity db exists)
    nil))

(defn ents
  "Converts a collection of entity IDs to their corresponding entities.

   Example:
   ```
   ;; Convert query results to entities
   (ents db [[:e1] [:e2] [:e3]])
   ```"
  [db results]
  (map (fn [result]
         (-> result
             first
             (ent db)))
       results))

(defn ent?
  "Returns true if x is a Datomic entity.

   Example:
   ```
   (def person (d/entity db 42))
   (ent? person) ;; => true
   (ent? 42)     ;; => false
   ```"
  [x]
  (instance? datomic.query.EntityMap x))

;; The following functions help when retrieving entities when you
;; don't need to specify their relationships to other entities
;; and you only have one input, the db

(defn- add-head
  [head seqs]
  (map #(into [head] %) seqs))

#_(defn- single-eid-where
    "Used to build where clauses for functions below"
    [eid [attr-or-condition & conditions]]
    (add-head eid
              (concat [(flatten [attr-or-condition])]
                      conditions)))

#_(defn- parse-conditions
    [eid conditions]
    (let [[where & opts] (partition-by #(or (= :in %) (= :inputs %)) conditions)]
      (merge {:where (single-eid-where eid where)
              :in    ['$]}
             (reduce merge {}
                     (map #(hash-map (ffirst %) (second %))
                          (partition 2 opts))))))

#_(defn- single-eid-query
    [find eid conditions]
    (let [parsed-conditions (parse-conditions eid conditions)]
      (apply d/q (merge {:find find}
                        (dissoc parsed-conditions :inputs))
             (:inputs parsed-conditions))))

(defn- no-relation-where
  "Used to build where clauses for functions below"
  [eid [attr-or-condition & conditions]]
  (add-head eid
            (concat [(flatten [attr-or-condition])]
                    conditions)))

(defn- no-relation-query-map
  [find variable conditions]
  {:find  find
   :where (no-relation-where variable conditions)
   :in    ['$]})

(defn- no-relation-query
  [db find variable conditions]
  (d/q (no-relation-query-map find variable conditions) db))

(defn eid-by
  "Returns entity ID of first entity matching conditions.

   Example:
   ```
   ;; Find ID of person with name 'Alice'
   (eid-by db [:person/name \"Alice\"])

   ;; Find ID with multiple conditions
   (eid-by db [:person/name \"Bob\"] [:person/age 42])
   ```"
  [db & conditions]
  (ffirst (no-relation-query db ['?x] '?x conditions)))

(defn one
  "Returns the first entity matching conditions, or nil if none found.

   Example:
   ```
   ;; Find person with name 'Justin Biebs'
   (one db [:person/name \"Justin Biebs\"])

   ;; Find person with specific name and birth year
   (one db [:person/name \"Alice\"] [:person/birth-year 1985])
   ```"
  [db & conditions]
  (when-let [id (apply eid-by db conditions)]
    (d/entity db id)))

(defn all
  "Returns all entities matching the specified conditions.

   Example:
   ```
   ;; Find all people (entities with :person/name attribute)
   (all db :person/name)

   ;; Find all people named 'Smith'
   (all db [:person/name \"Smith\"])

   ;; Find all people born in 1955 with favorite color burgundy
   (all db [:person/birth-year 1955] [:favorite/color \"burgundy\"])
   ```"
  [db & conditions]
  (ents db (no-relation-query db ['?x] '?x conditions)))

(defn ent-count
  "Returns the count of entities matching conditions.

   Example:
   ```
   ;; Count all people
   (ent-count db :person/name)

   ;; Count people born in 1970
   (ent-count db [:person/birth-year 1970])
   ```"
  [db & conditions]
  (or (ffirst (no-relation-query db '[(count ?x)] '?x conditions))
      0))

(defn pull-one
  "Pulls the first entity matching conditions using the specified pattern.
   Returns nil if no entity matches.

   Example:
   ```
   ;; Pull name and email for a specific person
   (pull-one db [:person/name :person/email] [:person/id \"P123\"])

   ;; Pull with nested pattern
   (pull-one db [:person/name {:person/address [:address/city :address/country]}]
            [:person/email \"alice@example.com\"])
   ```"
  [db pattern & conditions]
  (when-let [e (apply one db conditions)]
    (d/pull db pattern (:db/id e))))

(defn pull-all
  "Pulls all entities matching conditions using the specified pattern.

   Example:
   ```
   ;; Pull names of all people
   (pull-all db [:person/name] :person/name)

   ;; Pull names and emails of people born in 1980
   (pull-all db [:person/name :person/email] [:person/birth-year 1980])

   ;; Pull with nested attributes
   (pull-all db
            [:person/name {:person/addresses [:address/city :address/country]}]
            [:person/employee? true])
   ```"
  [db pattern & conditions]
  (->> (apply all db conditions)
       (keep :db/id)
       (map (partial d/pull db pattern))))
