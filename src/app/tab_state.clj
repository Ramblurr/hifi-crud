(ns app.tab-state
  (:import [java.time Instant Duration])
  (:require
   [hyperlith.core :as h]
   [clojure.core.async :as a]
   [chime.core :as chime]))

(def !page-state (atom {}))

@!page-state

(defn transact!
  "Helper function to be used by your command/action handlers.
  Example:
  (transact! tab-id #(assoc-in % [:some-state-flag] true))
  "
  [tab-id f]
  (if tab-id
    (swap! !page-state update tab-id (fn [state]
                                       (-> state
                                           (f)
                                           (assoc ::modified (System/currentTimeMillis)))))

    (throw (ex-info "No tab-id in request" {}))))

(defn init-tab-state!
  "Initializes a new tab state session. <ch is a channel that will be written to when the tab state changes."
  [<ch tab-id]
  (assert <ch "Channel must be provided")
  (assert tab-id "Tab ID must be provided")
  (swap! !page-state assoc tab-id {::created (System/currentTimeMillis)})
  (add-watch !page-state tab-id (fn [watch-key _ _ _]
                                  (when-not (a/>!! <ch :state-changed)
                                    (remove-watch !page-state watch-key)))))

(defn remove-tab-state!
  "Cleans up a tab state session"
  [tab-id]
  (swap! !page-state dissoc tab-id)
  (remove-watch !page-state tab-id))

;; Tab sessions older than this are pruned
(def STALE-THRESHOLD-HOURS 12)

(defn stale? [now created modified]
  (> (- now (or modified created)) (* STALE-THRESHOLD-HOURS 3600000)))

(defn clean-stale-page-state
  "Removes tab-ids that are stale, where stale is defined as not having been modified or created in the last 24 hours."
  [page-state]
  (let [now (System/currentTimeMillis)]
    (reduce-kv (fn [acc tab-id {:keys [::created ::modified]}]
                 (if (stale? now created modified)
                   (dissoc acc tab-id)
                   acc))
               page-state
               page-state)))

(defn clean-stale-watches!
  "Removes watches for tab-ids that are no longer in the page state."
  []
  (let [watches       (-> !page-state .getWatches keys)
        stale-watches (remove #(get @!page-state %) watches)]
    (doseq [watch-key stale-watches]
      (remove-watch !page-state watch-key))))

(defn start-clean-page-state-job
  "Starts a job that cleans stale page state every 10 seconds."
  []
  (chime/chime-at (chime/periodic-seq (Instant/now) (Duration/ofSeconds 60))
                  (fn [_]
                    (swap! !page-state clean-stale-page-state)
                    (clean-stale-watches!))))

(defn generate-tab-id []
  (h/new-uid))

(defn tab-state! [tab-id]
  (get @!page-state tab-id))
