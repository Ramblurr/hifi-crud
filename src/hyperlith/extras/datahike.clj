;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2


(ns hyperlith.extras.datahike
  "Datahike wrapper for hyperlith. Requires datalevin as a dependency."
  (:require [hyperlith.core :as h]
            [datahike-sqlite.core]
            [datahike.core :as dc]
            [datahike.api :as d]))

(def default-schema
  (merge
   #:session
    {:id   {:db/unique      :db.unique/identity
            :db/valueType   :db.type/string
            :db/index       true
            :db/cardinality :db.cardinality/one}
     :user {:db/valueType   :db.type/ref
            :db/index       true
            :db/cardinality :db.cardinality/one}}

   #:user
    {:id {:db/unique      :db.unique/identity
          :db/valueType   :db.type/uuid
          :db/index       true
          :db/cardinality :db.cardinality/one}}

   #:error
    {:id    {:db/unique      :db.unique/identity
             :db/valueType   :db.type/uuid
             :db/index       true
             :db/cardinality :db.cardinality/one}
     :cause {:db/valueType   :db.type/string
             :db/cardinality :db.cardinality/one}
     :trace {:db/valueType   :db.type/bytes
             :db/cardinality :db.cardinality/one}
     :type  {:db/valueType   :db.type/string
             :db/index       true
             :db/cardinality :db.cardinality/one}
     :data  {:db/valueType   :db.type/bytes
             :db/cardinality :db.cardinality/one}}

   #:error-event
    {;; Reified join so we get the date of when it happened
     :session {:db/valueType   :db.type/ref
               :db/index       true
               :db/cardinality :db.cardinality/one}
     :error   {:db/valueType   :db.type/ref
               :db/index       true
               :db/cardinality :db.cardinality/one}}))

(defn schema->tx-data
  [schema]
  (reduce (fn [schema [attr-id attr-map]]
            (conj schema (assoc attr-map :db/ident attr-id))) [] schema))

(def q d/q)
(def db d/db)
(def tx! d/transact!)
(def squuid dc/squuid)

(defn create-and-connect [db-path]
  (let [cfg {:store              {:backend      :sqlite
                                  :journal_mode "WAL"
                                  :synchronous  "NORMAL"
                                  :dbname       db-path}
             :schema-flexibility :write
             :heep-history?      true
             :initial-tx         {:tx-data (schema->tx-data default-schema)}}]
    (when-not (d/database-exists? cfg)
      (d/create-database cfg :initial-tx {:tx-data (schema->tx-data default-schema)}))
    (d/connect cfg)))

(defn ctx-start [ctx db-path]
  (let [conn (create-and-connect db-path)]
    (assert (some? conn))
    (d/listen conn :refresh-on-change
              (fn [_] (h/refresh-all!)))
    (assoc ctx :conn conn)))

(defn ctx-stop [{:keys [conn] :as _ctx}]
  (try
    (d/unlisten conn :refresh-on-change)
    (d/release conn)
    (catch Exception e
      (tap> e))))

(comment

  (defn log-on-error [{:keys [db] :as _ctx} {:keys [req error]}]
    (let [sid (or (:sid req) "no-sid")
          txs [{:session/id sid} ;; users might not have a session in the db
               (h/qualify-keys
                (dissoc error :via)
                :error)
               (h/qualify-keys
                {:session [:session/id sid]
                 :error   [:error/id (:id error)]}
                :error-event)]]
      (try ;; tx! can fail if data contains un-serialisable items
        @(tx! db txs)
        (catch Throwable _
          ;; if data elements can't be serialized we remove them
          @(tx! db (update txs 1 dissoc :error/data))))))

  #_(defn backup-copy!
      "Make a backup copy of the database. `dest-dir` is the destination
  data directory path. Will compact while copying if `compact?` is true."
      [conn dest-dir compact?]
      (let [lmdb (.-lmdb ^Store (.-store ^DB conn))]
        (println "Copying...")
        (l/copy lmdb dest-dir compact?)
        (println "Copied database.")))

  (defn tuples
    "Returns the set of tuples that represent a nested map/vector. Lets you
  use datalog query engines to query json/edn data."
    ([root] {:pre [(or (map? root) (vector? root))]}
            (tuples [] root))
    ([parent x]
     (cond (map? x)
           (mapcat (fn [[k v]] (tuples (conj parent k) v)) x)

           (vector? x)
           (mapcat (fn [i v] (tuples (conj parent i) v)) (range) x)

           :else [(conj parent x)]))))

(defn find-all
  "Returns a list of all entities having attr"
  [db attr pattern]
  (d/q '[:find (pull ?e pattern)
         :in $ ?attr pattern
         :where
         [?e ?attr ?v]]
       db attr pattern))

(defn count-all
  "Returns the number of entities having attr"
  [db attr]
  (let [result
        (ffirst (d/q '[:find (count ?e)
                       :in $ ?attr
                       :where [?e ?attr ?v]]
                     db attr))]
    (if (nil? result)
      0
      result)))

(defn count-by
  "Count the number of entities possessing attribute attr"
  [db attr]
  (->> (d/q '[:find (count ?e)
              :in $ ?attr
              :where [?e ?attr]]
            db attr)
       ffirst))

(defn find-all-by
  "Returns the entities having attr and val"
  [db attr attr-val pattern]
  (d/q '[:find (pull ?e pattern)
         :in $ ?attr ?val pattern
         :where [?e ?attr ?val]]
       db attr attr-val pattern))

(defn find-by
  "Returns the unique entity identified by attr and val."
  [db attr attr-val pattern]
  (ffirst (find-all-by db attr attr-val pattern)))
