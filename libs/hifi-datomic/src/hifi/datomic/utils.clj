;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.datomic.utils
  (:require
   [cognitect.anomalies :as anomalies]))

;; borrowed from: https://github.com/Datomic/ion-starter/blob/23af1ccc7d9e950bc8a69b799f13327babf0dce4/src/datomic/ion/starter/utils.clj

(def retryable-anomaly?
  "Set of retryable anomalies."
  #{::anomalies/busy
    ::anomalies/unavailable
    ::anomalies/interrupted})

(defn with-retry
  "Try op, return result if successful, if op throws, check exception against retry? pred,
  retrying if the predicate returns true. Optional backoff function controls retry wait. Backoff
  function should return msec backoff desired or nil to stop retry.
  Defaults to trying 10 times with linear backoff. "
  [op & {:keys [retry? backoff]
         :or   {retry?  (fn [e]
                          (-> e ex-data ::anomalies/category retryable-anomaly?))
                backoff (fn [epoch]
                          (when (<= epoch 10)
                            (* 200 epoch)))}}]
  (loop [epoch 1]
    (let [[success val] (try [true (op)]
                             (catch Exception e
                               [false e]))]
      (if success
        val
        (if-some [ms (and (retry? val) (backoff epoch))]
          (do
            (Thread/sleep ms)
            (recur (inc epoch)))
          (throw val))))))
