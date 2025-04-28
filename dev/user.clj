;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT


(ns user)

(defn dev
  "Load and switch to the 'dev' namespace."
  []
  (require 'dev)
  (in-ns 'dev)
  :loaded)

(dev)

(comment
  (require
   '[datahike-sqlite.core]
   '[datahike.api :as d])

  (def cfg {:store {:backend      :sqlite
                    :journal_mode "WAL"
                    :synchronous  "NORMAL"
                    :dbname       "for-the-ceo.sqlite"}})

  (d/database-exists? cfg)
  ;; => false

  (d/create-database cfg)

  ;; this will delete the table in the sqlite file,
  ;; but will not delete the sqlite file itself
  (d/delete-database cfg)

  (def conn (d/connect cfg))
  1

  (d/transact conn [{:db/ident       :name
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one}
                    {:db/ident       :awesome?
                     :db/valueType   :db.type/boolean
                     :db/cardinality :db.cardinality/one}])

  (d/transact conn [{:name "React" :awesome? false}
                    {:name "datastar" :awesome? true}])

  (d/q '[:find (pull ?e [*])
         :in $ ?name
         :where [?e :name ?name]]
       @conn "React")
  (d/release conn))
