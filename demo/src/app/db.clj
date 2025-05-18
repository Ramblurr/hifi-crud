;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns app.db
  (:require
   [hifi.datastar :as datastar]
   [datomic.api :as d]))

(defn schema->tx-data
  [schema]
  (reduce (fn [schema [attr-id attr-map]]
            (conj schema (assoc attr-map :db/ident attr-id))) [] schema))

(def q d/q)
(def db d/db)
(def tx! d/transact-async)
(def squuid d/squuid)

(defn create-and-connect [db-uri]
  (d/create-database db-uri)
  (d/connect db-uri))

(defn start [{:keys [db-uri]}]
  (assert db-uri)
  (let [conn (create-and-connect db-uri)]
    (assert (some? conn))
    #_(d/listen conn :refresh-on-change
                (fn [_] (datastar/rerender-all!)))
    conn))

(defn stop [conn]
  (try
    #_(d/unlisten conn :refresh-on-change)
    (d/release conn)
    (catch Exception e
      (tap> e))))

(defn datomic-middleware [conn]
  {:name ::datomic-middleware
   :wrap (fn [handler]
           (fn [req]
             (handler (assoc req :conn conn))))})

(comment

  (defn log-on-error [{:app/keys [db] :as _ctx} {:keys [req error]}]
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

(defn unique-attr-available? [attr db value]
  (nil? (find-by db attr value [attr])))
