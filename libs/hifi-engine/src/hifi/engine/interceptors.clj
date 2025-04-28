;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

(ns hifi.engine.interceptors
  (:require
   [hifi.engine.context :as context]
   [promesa.core :as pr]))

(defn do-effect
  "Execute an effect returning a promise."
  [ctx {:effect/keys [kind data]}]
  ;; (tap> [:do-effect kind])
  (let [handler (context/get-effectuator ctx kind)]
    (if (some? handler)
      (pr/let [r (handler ctx data)]
        {:result/kind kind
         :result/data r})
      (throw
       (context/ex ::no-fx-handler {:id kind})))))

(defn do-effects*
  [ctx effects]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (pr/loop [results-effects [[] effects]]
    (let [[results remaining-effects] results-effects
          effect                      (first remaining-effects)]
      (pr/handle
       (try
         (when effect
           (do-effect ctx effect))
         (catch Exception e
           (pr/rejected e)))
       (fn do-effects-handler [result error]
         (cond
           (some? error)
           error

           (empty? (rest remaining-effects))
           [(conj results result) []]

           :else
           #_{:clj-kondo/ignore [:invalid-arity]}
           (pr/recur [(conj results result) (rest remaining-effects)])))))))

(def do-fx-interceptor
  {:interceptor/name :do-fx-interceptor
   :doc              "Executes the effects in the outcome"
   :leave

   ;; orig version that allows fx to return results
   #_(fn do-fx-leave [ctx]
       (pr/let [results (-> (do-effects* ctx (context/get-effects ctx))
                            (pr/catch #(throw %)))]
         (assoc ctx :results
                {:outcome/results (first results)})))
   (fn do-fx-leave [ctx]
     (pr/let [_ (-> (do-effects* ctx (context/get-effects ctx))
                    (pr/catch #(throw %)))]
       ctx))})

(def report-unhandled-error-interceptor
  {:interceptor/name :report-unhandled-error-interceptor
   :doc              "Reports unhandled errors to the logger"
   :error            (fn report-unhandled-error [_ctx error]
                       (tap> [:unhandled-error error])
                       (throw error))})

(def unhandled-error-interceptor
  {:interceptor/name :unhandled-error-interceptor
   :doc              "The final error interceptor which will wrap and throw the error"
   :error            (fn report-unhandled-error [ctx error]
                       (throw (ex-info "Unhandled error"
                                       {::context ctx}
                                       error)))})
