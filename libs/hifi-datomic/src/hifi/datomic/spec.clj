(ns hifi.datomic.spec
  (:import
   [datomic Connection]
   [java.time Duration]))

(def DatomicDbUriSchema [:re {:error/message "should be a datomic db-uri string "} #"datomic:.*"])
(def DatomicConnectionSchema [:fn #(instance? Connection %)])
(def DurationSchema [:fn #(instance? Duration %)])

(def MigrationDataSchema
  [:map {:doc "Data describing a datomic migration consisting of only tx-data"}
   [:id {:doc "The migration id, used for idempotency"} :keyword]
   [:tx-data [:vector :any]]])

(def MigrationFnSchema
  [:map {:doc "Data describing a migration function that returns tx-data"}
   [:id {:doc "The migration id, used for idempotency"} :keyword]
   [:tx-data-fn {:doc "A fully qualified 1-arity function that accepts a database connection and returns tx-data"}
    [:fn [:or fn? :qualified-symbol]]]])

(def MigrationInputSchema
  [:sequential
   {:min 0
    :doc "A migration input, canbe a string path to a migration file, a map with :id and :tx-data keys, or a map with :id and :tx-data-fn keys"}
   [:or :string MigrationFnSchema MigrationDataSchema]])

(def OutboxConsumerSchema
  [:map
   [:queue-id :keyword]
   [:handler-fn [:fn fn?]]])

(def DatomicConnectionComponentOptions
  [:map
   [:db-uri DatomicDbUriSchema]])

(def YoltqOptions
  ;; last updated 2025-05-16
  ;; https://github.com/ivarref/yoltq/blob/ae49a7ec82ecd3988e0f7825b0adead1dc77c911/src/com/github/ivarref/yoltq.clj#L22
  [:map
   [:max-retries {:optional true
                  :default  9223372036854775807
                  :doc      "Default number of times a queue job will be retried before giving up. Can be overridden on a per-consumer basis with (yq/add-consumer! :q (fn [payload] ...) {:max-retries 200}). If you want no limit on the number of retries, specify the value `0`. That will set the effective retry limit to 9223372036854775807 times."}
    :int]
   [:error-backoff-time {:optional true
                         :default  (Duration/ofSeconds 5)
                         :doc      "Minimum amount of time to wait before a failed queue job is retried"}
    DurationSchema]

   [:max-execute-time {:optional true
                       :default  (Duration/ofMinutes 5)
                       :doc      "Max time a queue job can execute before an error is logged"}
    DurationSchema]

   [:hung-backoff-time {:optional true
                        :default  (Duration/ofMinutes 30)
                        :doc      "Amount of time an in progress queue job can run before it is considered failed and will be marked as such"}
    DurationSchema]

   [:init-backoff-time {:optional true
                        :default  (Duration/ofSeconds 60)
                        :doc      "Backoff time for the init poller to avoid unnecessary compare-and-swap lock failures with the tx-report-queue listener"}
    DurationSchema]

   [:healthy-allowed-error-time {:optional true
                                 :default  (Duration/ofMinutes 15)
                                 :doc      "Time to allow failures before marking the system as unhealthy, useful when dealing with flaky downstream services"}
    DurationSchema]

   [:poll-delay {:optional true
                 :default  (Duration/ofSeconds 10)
                 :doc      "How frequent polling for init, error and hung jobs should be done"}
    DurationSchema]

   [:pool-size {:optional true
                :default  4
                :doc      "Specifies the number of threads available for executing queue and polling jobs. The final thread pool will be this size + 2"}
    :int]

   [:capture-bindings {:optional true
                       :doc      "Bindings to capture for queue job execution context"}
    vector?]

   [:system-error-poll-delay {:optional true
                              :default  (Duration/ofMinutes 1)
                              :doc      "How often should the system be polled for failed queue jobs"}
    DurationSchema]

   [:system-error-callback-backoff {:optional true
                                    :default  (Duration/ofHours 1)
                                    :doc      "How often should the system invoke error callbacks"}
    DurationSchema]

   [:auto-migrate? {:optional true
                    :default  true
                    :doc      "Should old, possibly stalled jobs be automatically migrated when the outbox is started?"}
    :boolean]])

(def DatomicMigratorComponentConfig
  [:map
   [:conn DatomicConnectionSchema]
   [:migration-data MigrationInputSchema]])

(def conn-component :conn)
(def migrator-component :migrator)
(def migration-data-component :migration-data)
(def tx-report-mult-component :tx-report-mult)
(def outbox-component :outbox)
(def outbox-consumer-component :outbox-consumers)
