(ns hifi.datomic.outbox
  "System plumbing for the Datomic outbox a transactional job queue"
  (:require [com.github.ivarref.yoltq :as yq]
            [clojure.tools.logging :as log]
            [hifi.error.iface :as pe]
            [hifi.datomic.spec :as spec]
            [promesa.exec.csp :as sp])
  (:import  [datomic Connection]
            [java.util.concurrent LinkedBlockingQueue BlockingQueue TimeUnit]))

(defn tx-report-mult->queue [mult <cancel]
  (let [<ch       (sp/tap! mult (sp/chan :buf 32))
        out-queue (LinkedBlockingQueue.)]
    (sp/go-loop []
      (let [[report ch] (sp/alts! [<cancel <ch]
                                  :priority true)]
        (condp = ch
          <cancel (do
                    (sp/close! <ch)
                    (sp/close! <cancel))
          <ch     (let [ok-offer (.offer ^BlockingQueue out-queue report 30 TimeUnit/MINUTES)]
                    (when (false? ok-offer)
                      (log/error "Failed to offer item in multicaster to yoltq"))
                    (recur)))))
    out-queue))

(defn start-outbox! [{:keys [tx-report-mult conn consumers]} opts]
  (let [<cancel (sp/chan)]
    (assert (instance? Connection conn))
    (yq/init! (merge
               opts
               {:conn            conn
                :tx-report-queue (tx-report-mult->queue tx-report-mult <cancel)}))
    (doseq [{:keys [queue-id handler-fn]} consumers]
      (yq/add-consumer! queue-id handler-fn)
      (log/info (str "Added outbox consumer for " queue-id)))
    (yq/start!)
    {:<cancel <cancel}))

(defn stop-outbox! [instance]
  (sp/put (:<cancel instance) :cancel)
  (yq/stop!))

(defn DatomicOutboxComponent
  "Donut system component for a the Datomic transactional outbox implemented with yoltq"
  [options-ref]
  {:donut.system/start  (fn [{config :donut.system/config}]
                          (let [opts (pe/coerce! spec/YoltqOptions (:hifi/options config))]
                            (start-outbox! config opts)))
   :donut.system/stop   (fn stop-db [{instance :donut.system/instance}]
                          (stop-outbox! instance))
   :hifi/options-schema spec/YoltqOptions
   :hifi/options-ref    options-ref
   :donut.system/config {:conn           [:donut.system/local-ref [spec/conn-component]]
                         :tx-report-mult [:donut.system/local-ref [spec/tx-report-mult-component]]
                         :consumers      [:donut.system/local-ref [spec/outbox-consumer-component]]}})
