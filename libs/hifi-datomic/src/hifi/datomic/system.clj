;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.datomic.system
  (:require
   [com.fulcrologic.guardrails.malli.core :refer [=> >defn-]]
   [datomic.api :as d]
   [hifi.datomic.migrations :as migrations]
   [hifi.datomic.outbox :as outbox]
   [hifi.datomic.report-queue :as report-queue]
   [hifi.datomic.spec :as spec]
   [taoensso.trove :as trove]))

(>defn- ensure-and-connect [{:keys [db-uri]}]
        [spec/DatomicConnectionComponentOptions => spec/DatomicConnectionSchema]
        (when (d/create-database db-uri)
          (trove/log! {:msg "Datomic database created"}))
        (d/connect db-uri))

(defn DatomicConnectionComponent
  "Donut system component for a Datomic Database connection"
  [options-ref]
  {:donut.system/start      (fn start-db [{config :donut.system/config}]
                              (let [conn (ensure-and-connect (:hifi/options config))]
                                (trove/log! {:msg "Datomic database started successfully"})
                                conn))
   :donut.system/post-start (fn post-start-db [{:donut.system/keys [instance config]}]
                              (trove/log! {:msg (format "Ensuring %d Datomic schema migrations installed" (count (:migration-data config)))})
                              (migrations/install-schema instance (:migration-data config)))
   :donut.system/stop       (fn stop-db [{instance :donut.system/instance}]
                              (trove/log! {:msg "Shutting down Datomic connection"})
                              (d/release instance)
                              (d/shutdown false))
   :donut.system/config     {:migration-data [:donut.system/local-ref [spec/migration-data-component]]}
   :hifi/options-schema     spec/DatomicConnectionComponentOptions
   :hifi/options-ref        options-ref})

(defn DatomicComponentGroup
  "Donut system component group for Datomic and related services

  `component-group-key` is the keyword under which this component group will be registered in the top-level system defs."
  [{:keys [component-group-key
           migration-data]
    :or   {migration-data []}}]
  (assert (keyword? component-group-key) "DatomicComponentGroup options `component-group-key` must be a keyword (e.g., :your-app/datomic)")
  {;; The Datomic connection
   spec/conn-component            (DatomicConnectionComponent [:env component-group-key spec/conn-component])
   ;; Schema migration data
   spec/migration-data-component  migration-data
   ;; tx-report-queue multicasting w/ promesa
   spec/tx-report-mult-component  report-queue/DatomicReportQueueMulticastComponent
   ;; the transactional outbox for job queues
   spec/outbox-component          (outbox/DatomicOutboxComponent [:env component-group-key spec/outbox-component])
   spec/outbox-consumer-component {}})
