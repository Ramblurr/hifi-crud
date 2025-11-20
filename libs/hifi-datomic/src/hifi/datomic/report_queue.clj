(ns hifi.datomic.report-queue
  "Multicast the datomic tx-report-queue to consumers using promesa csp"
  (:require
   [datomic.api :as d]
   [hifi.datomic.spec :as spec]
   [promesa.core :as pr]
   [promesa.exec.csp :as sp]
   [taoensso.trove :as trove])
  (:import
   (datomic Connection)
   (java.util.concurrent BlockingQueue TimeUnit)))

(defn start-report-queue-thread!
  "On a separate thread, take values from the `tx-report-queue` over `conn` and
  put them onto channel `ch`. "
  [conn ch]
  (assert (instance? Connection conn))
  (let [ready? (pr/deferred)]
    (pr/vthread
     (trove/log! {:level :debug :msg "Starting tx-report-queue multicasting"})
     (try
       (let [input-queue (d/tx-report-queue conn)]
         (loop []
           (let [report (.poll ^BlockingQueue input-queue 1 TimeUnit/SECONDS)]
             (pr/resolve! ready? :ready)
             (when (some? report)
               (if (sp/put! ch report)
                 (recur)
                 (sp/close! ch))))))
       (catch InterruptedException _
         (pr/resolve! ready? :interrupted)
         (sp/close! ch)
         (trove/log! {:msg "Stopping tx-report-queue multicasting"}))
       (catch Throwable t
         (pr/resolve! ready? :error)
         (tap> [::error t])
         (trove/log! {:level :error :error t :msg "Unexpected error in tx-report-queue thread"}))
       (finally
         (trove/log! {:msg "Stopped tx-report-queue multicasting"})
         (d/remove-tx-report-queue conn))))

    ready?))

(defn start-multicast! [conn]
  (let [tx-report-mult     (sp/mult)
        mult-start-promise (start-report-queue-thread! conn tx-report-mult)
        start-result       (deref mult-start-promise (* 30 60000) :timeout)]
    (cond (= start-result :timeout)
          (throw (RuntimeException. "Timed out waiting for tx-report-queue multicast to start"))
          (= start-result :error)
          (throw (RuntimeException. "Error during tx-report-queue multicast start"))
          (= start-result :ready)
          (trove/log! {:level :debug :msg "tx-report-queue multicast is ready"})
          :else
          (throw (RuntimeException. (str "Unexpected resuls statuts from tx-report-queue multicast: " start-result))))
    tx-report-mult))

(defn stop-multicast! [tx-report-mult]
  (sp/close! tx-report-mult))

(def DatomicReportQueueMulticastComponent
  "Donut system component for managing the lifecycle of multicasting the Datomic tx-report-queue"
  {:donut.system/start  (fn start-db-mult [{config :donut.system/config}]
                          (start-multicast! (:conn config)))
   :donut.system/stop   (fn stop-db-mult [{instance :donut.system/instance}]
                          (stop-multicast! instance))
   :donut.system/config {:conn [:donut.system/local-ref [spec/conn-component]]}})
