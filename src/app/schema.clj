;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT


(ns app.schema
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [app.crypto :as crypto]
   [taoensso.tempel :as tempel]
   [hyperlith.core :as h]
   [hyperlith.extras.datahike :as d]))

(def schema
  (d/schema->tx-data
   (merge
    #:user
    {:email    {:db/unique      :db.unique/value
                :db/valueType   :db.type/string
                :db/index       true
                :db/cardinality :db.cardinality/one}
     :keychain {:db/valueType   :db.type/bytes
                :db/cardinality :db.cardinality/one}})))

(defn ecommerce-schema []
  (d/schema->tx-data
   (apply merge
          (edn/read-string (slurp (io/resource "app/schema/ecommerce.edn"))))))

(defn install-schema! [conn]
  @(d/tx! conn {:tx-data schema})
  @(d/tx! conn {:tx-data (ecommerce-schema)}))

(defn create-root!
  "Create the root user"
  [conn]
  (when (not (d/find-by @conn :user/email "admin" [:user/email]))
    @(d/tx! conn {:tx-data [{:user/id       (d/squuid)
                             :user/email    "admin"
                             :user/keychain (crypto/create-root-key (h/env :root-secret))}]})))

(defn root-public-keychain
  "Return the public keychain of the root user."
  [conn]
  (->
   (d/find-by @conn :user/email "admin" [:user/keychain])
   :user/keychain
   tempel/public-data
   :keychain))

(defn ctx-start [{:keys [conn] :as ctx}]
  (assert conn "No connection to Datahike")
  (install-schema! conn)
  (create-root! conn)
  (assoc ctx :app/root-public-keychain (root-public-keychain conn)))
