(ns app.schema
  (:require [hyperlith.extras.datahike :as d]))

(def schema
  (d/schema->tx-data
   (merge

    #:user
    {:email         {:db/unique      :db.unique/value
                     :db/valueType   :db.type/string
                     :db/index       true
                     :db/cardinality :db.cardinality/one}
     :password-hash {:db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one}})))

(defn install-schema! [conn]

  @(d/tx! conn {:tx-data schema}))

(defn ctx-start [{:keys [conn] :as ctx}]
  (assert conn "No connection to Datahike")
  (install-schema! conn)
  ctx)
