;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.datastar.ring
  (:require
   [hifi.datastar :as datastar]
   [hifi.datastar.tab-state :as tab-state]
   [starfederation.datastar.clojure.adapter.common :as d*com]
   [starfederation.datastar.clojure.adapter.ring :as ring-gen]
   [starfederation.datastar.clojure.api :as d*]))

(defn render-handler
  "A default ring handler for rendering views over a long-lived SSE connection with datastar.

    - `render-fn` is an arity-1 function of req that returns a string of HTML sent to the client with D*'s merge-fragment

   Opts are:
    - `init-tab-state-fn` is an arity-1 pure-function of state and returns the initialized state.
                          On a first-connect/refresh the state will be nil, but on an SSE reconnect the state will be whatever
                          the state was before the disconnect.

  This handler is a basic implementation, if you outgrow it, copy and paste it into your codebase to extend it.
  "
  ([render-fn]
   (render-handler render-fn nil))
  ([render-fn {:keys [init-tab-state-fn]}]
   (let [init-tab-state-fn (or init-tab-state-fn #(identity %2))]
     (fn [req]
       (let [ctx             (datastar/long-lived-render-setup req render-fn)
             first-tab-state (tab-state/init-tab-state! req (:hifi.datastar/<render ctx) #(init-tab-state-fn req %))
             req             (assoc req :hifi.datastar/tab-state first-tab-state)]
         (ring-gen/->sse-response req
                                  {:headers            {}
                                   ring-gen/on-open    (fn [sse-gen]
                                                         (datastar/long-lived-render-on-open ctx req sse-gen))
                                   ring-gen/on-close   (fn [_ _]
                                                         (datastar/long-lived-render-on-close ctx)
                                                         ;; If you wanted to reset on refresh or disconnect,
                                                         ;; otherwise tab state will be cleaned in the background after some hours
                                                         #_(when tab-id
                                                             (tab-state/remove-tab-state! req)))
                                   d*com/write-profile (datastar/brotli-write-profile)}))))))

(defn action-handler
  "A default http-kit ring handler for executing commands, side effects which might return signals to be merged. "
  [handler]
  (fn [req]
    (let [{:hifi.datastar/keys [signals]} (handler req)]
      (if signals
        (ring-gen/->sse-response req {:headers            (merge (:security-headers req) {"Cache-Control" "no-store"})
                                      ring-gen/on-open    (fn [sse-gen]
                                                            (d*/merge-signals! sse-gen (datastar/edn->json signals))
                                                            (d*/close-sse! sse-gen))
                                      ring-gen/on-close   (fn [_ _])
                                      d*com/write-profile (datastar/brotli-write-profile)})
        {:status  204
         :headers {"Cache-Control" "no-store"}}))))

(defn action-handler-async
  [handler]
  (fn [req]
    (ring-gen/->sse-response req {:headers         (merge (:security-headers req) {"Cache-Control" "no-store"})
                                  ring-gen/on-open (fn [sse-gen]
                                                     (try
                                                       (handler req sse-gen)
                                                       (catch Throwable t
                                                         (d*/close-sse! sse-gen))))

                                  ring-gen/on-close   (fn [_ _])
                                  d*com/write-profile (datastar/brotli-write-profile)})))
