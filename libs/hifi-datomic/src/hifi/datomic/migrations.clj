;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.datomic.migrations
  (:require [com.fulcrologic.guardrails.malli.core :refer [=> >defn- >defn]]
            [clojure.tools.logging :as log]
            [datomic.api :as d]
            [hifi.datomic.spec :as spec]
            [dev.gethop.stork :as stork]))

(>defn install-schema
       "Installs schema migrations into a Datomic database. This function is designed
   to be called at system startup to ensure all necessary schema is in place.

   The function handles both direct migration data and migration files (as resources).
   Each migration is guaranteed to be executed exactly once, making it safe to call
   this function multiple times on system restart.

   Examples:
   ```clojure
   ;; Install schema from a direct map
   (install-schema conn
                  [{:id :person-schema
                    :tx-data [{:db/ident :person/id
                               :db/valueType :db.type/uuid
                               :db/cardinality :db.cardinality/one}]}])

   ;; Install schema from resource files
   (install-schema conn
                  [\"schemas/person.edn\"
                   \"schemas/order.edn\"])

   ;; Mix of direct data and file paths
   (install-schema conn
                  [{:id :base-schema, :tx-data [...]}
                   \"schemas/extended.edn\"])
   ```

   Args:
     - conn: A Datomic connection
     - migrations: A collection of migrations, where each item can be either:
       - A string path to a migration EDN file (resource path)
       - A map with :id and :tx-data keys for direct schema definition
       - A map with :id and :tx-data-fn keys where :tx-data-fn is a 1-arity function accepting the conn and returning tx-data"
       [conn migrations]
       [spec/DatomicConnectionSchema spec/MigrationInputSchema => :any]
       (run!
        (fn [m]
          (if-let [migration (if (string? m)
                               (stork/read-resource m) m)]
            (when (not= (stork/ensure-installed conn migration)
                        :dev.gethop.stork/already-installed)
              (log/info "Installed datomic migration: " (:id migration)))
            (throw (ex-info "Invalid migration data was encountered aborting schema installation."
                            {:migration m}))))
        migrations))

(defn show-schema
  "Returns all custom schema entities (attributes, enums, etc.) installed in the database,
   filtering out system namespaces.

   This function is useful for debugging and verifying that schema has been properly
   installed.

   Example:
   ```clojure
   ;; Show all custom schema in the database
   (show-schema conn)
   ;; => [[:person/id] [:person/name] [:order/id] ...]
   ```

   Args:
     - conn: A Datomic connection

   Returns:
     A collection of tuples, each containing a single keyword representing a schema entity."
  [conn]
  (let [system-ns #{"db" "db.type" "db.install" "db.part"
                    "db.lang" "fressian" "db.unique" "db.excise"
                    "db.cardinality" "db.fn" "db.sys" "db.bootstrap"
                    "db.alter"}]
    (d/q '[:find ?ident
           :in $ ?system-ns
           :where
           [?e :db/ident ?ident]
           [(namespace ?ident) ?ns]
           [((comp not contains?) ?system-ns ?ns)]]
         (d/db conn) system-ns)))
