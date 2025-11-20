;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.datastar.tab-state
  (:require
   [chime.core :as chime]
   [com.fulcrologic.guardrails.malli.core :refer [=> >defn]]
   [hifi.datastar.spec :as spec]
   [hifi.util.shutdown :as shutdown]
   [promesa.exec.csp :as sp]
   [taoensso.nippy :as nippy]
   [taoensso.trove :as trove])
  (:import
   [java.time Duration Instant]))

(def DurationSchema [:fn #(instance? Duration %)])
(def InstantSchema [:fn #(instance? Instant %)])

(defn tab-state!
  ([{:hifi.datastar/keys [tab-state-store tab-id] :as _req}]
   (tab-state! tab-state-store tab-id))
  ([ts-store tab-id]
   (when (and tab-id ts-store)
     (get @ts-store tab-id))))

(defn transact!
  "Helper function to be used by your command/action handlers.
  Example:
  (transact! tab-id #(assoc-in % [:some-state-flag] true))
  "
  ([{:hifi.datastar/keys [tab-state-store tab-id] :as _req} f]
   (transact! tab-state-store tab-id f))
  ([ts tab-id f]
   (if tab-id
     (swap! ts update tab-id (fn [state]
                               (let [created (::created state)]
                                 (-> state
                                     (f)
                                     (assoc
                                      ::created created
                                      ::modified (Instant/now))))))

     (throw (ex-info "No tab-id in request" {})))))

(defn init-tab-state!
  "Initializes a tab state session for the first time or as a reconnect.

    - req - a map with `:hifi.datastar/tab-state-store` and `:hifi.datastar/tab-id` `ts`
    - `<ch` is a channel that will be written to when the tab state changes
    - `init-fn` is an arity-1 pure-function of state and returns the initialized state.
                On a first-connect/refresh the state will be nil, but on an SSE reconnect the state will be whatever
                the state was before the disconnect.
  Returns the updated tab state."
  ([req <ch]
   (init-tab-state! req <ch nil))
  ([{:hifi.datastar/keys [tab-state-store tab-id]} <ch init-fn]
   (assert <ch "Channel must be provided")
   (when (some? tab-id)
     (swap! tab-state-store update
            tab-id (fn [state]
                     (-> (or state {})
                         (init-fn)
                         (assoc ::created (Instant/now)))))
     (add-watch tab-state-store tab-id (fn [watch-key _ _ _]
                                         (when-not (sp/put! <ch [:state-changed])
                                           (remove-watch tab-state-store watch-key))))
     (tab-state! tab-state-store tab-id))))

(defn remove-tab-state!
  "Cleans up a tab state session"
  [{:hifi.datastar/keys [tab-state-store tab-id]}]
  (swap! tab-state-store dissoc tab-id)
  (remove-watch tab-state-store tab-id))

(>defn default-clean? [{:keys [clean-age-threshold]}
                       now _ {:keys [::modified ::created]}]
       [[:map [:clean-age-threshold DurationSchema]]
        InstantSchema
        :any
        [:map
         [::modified {:optional true} InstantSchema]
         [::created InstantSchema]] => :boolean]

       (.isBefore (or modified created) (.minus now clean-age-threshold)))

(defn clean-stale-tab-state
  "Removes tab-ids that are stale, where stale is defined as not having been modified or created in the last 24 hours."
  [{:keys [clean-predicate]
    :or   {clean-predicate default-clean?}
    :as   opts} tab-state]
  (let [now (Instant/now)]
    (reduce-kv (fn [acc tab-id s]
                 (if (clean-predicate opts now tab-id s)
                   (dissoc acc tab-id)
                   acc))
               tab-state
               tab-state)))

(defn clean-stale-watches!
  "Removes watches for tab-ids that are no longer in the tab state."
  [!tab-state-store]
  (let [watches       (-> !tab-state-store .getWatches keys)
        stale-watches (remove #(get @!tab-state-store %) watches)]
    (doseq [watch-key stale-watches]
      (remove-watch !tab-state-store watch-key))))

(defn start-clean-tab-state-job
  "Starts a job that cleans stale tab state every 10 seconds."
  [{:keys [clean-job-period !tab-state-store]
    :as   opts}]
  (chime/chime-at (chime/periodic-seq (Instant/now) clean-job-period)
                  (fn [_]
                    (swap! !tab-state-store (partial clean-stale-tab-state opts))
                    (clean-stale-watches! !tab-state-store))))

(defn load! [!store file]
  (try
    (reset! !store (nippy/thaw-from-file file))
    (catch java.io.FileNotFoundException e
      (trove/log! {:level :warn :msg "tab-state file does not exist, this is expected on the first start" :data {:file file} :error e}))))

(defn persist! [!store file]
  (try
    (nippy/freeze-to-file file @!store)
    (catch Exception e
      (trove/log! {:level :error :msg "Could not save tab-state" :data {:file file} :error e}))))

(def TabStateComponent
  {:donut.system/start (fn  [{{:keys [:hifi/options]} :donut.system/config}]
                         (let [!tab-state-store (atom {})
                               store-filename   (:store-filename options)]
                           (when store-filename
                             (load! !tab-state-store store-filename)
                             (shutdown/add-shutdown-hook! ::persist (partial persist! !tab-state-store store-filename)))
                           {:!tab-state-store !tab-state-store
                            :clean-job        (start-clean-tab-state-job (assoc options
                                                                                :!tab-state-store !tab-state-store))
                            :store-filename   store-filename}))

   :donut.system/stop   (fn [{{:keys [!tab-state-store clean-job store-filename]} :donut.system/instance}]
                          (when clean-job
                            (.close clean-job))
                          (when store-filename
                            (persist! !tab-state-store store-filename)
                            (shutdown/remove-shutdown-hook! ::persist)))
   :donut.system/config {}
   :hifi/options-schema spec/TabStateComponentOptions
   :hifi/options-ref    [:hifi/components :options :tab-state]})
