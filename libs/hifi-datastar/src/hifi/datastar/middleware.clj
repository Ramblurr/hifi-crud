(ns hifi.datastar.middleware
  (:require
   [charred.api :as charred]
   [starfederation.datastar.clojure.api :as d*]))

;; -----------------------------------------------------------------------------
;; Datastar Signals Middleware

(def default-read-json (charred/parse-json-fn {:async? false :bufsize 1024 :key-fn keyword}))

(defn datastar-signals-middleware
  "Middleware to parse and extract datastar signals from the request

   This middleware adds these keys to the request map:
    :datastar-signals - a map of datastar signals

  The middleware options are:
    - read-json - an arity/1 function accepting the raw signal data and returning the parsed json as edn. defaults to a charred parse fn"
  [{:keys [read-json] :or {read-json default-read-json}}]
  {:name ::datastar-signals
   :wrap (fn wrap-datastar-sigals
           [handler]
           (fn [req]
             (if (d*/datastar-request? req)
               (if-let [signals (d*/get-signals req)]
                 (let [signals-edn (read-json signals)]
                   (handler (assoc req
                                   :datastar-signals signals-edn
                                   :submitted-csrf-token (:csrf signals-edn))))
                 (handler req))
               (handler req))))})

(def DatastarSignalsMiddlewareComponentData
  {:name           ::datastar-signals
   :options-schema [:map
                    [:read-json
                     {:doc "An arity/1 function accepting the raw signal data and returning the parsed json as edn. defaults to a charred parese fn"}
                     (fn? default-read-json)]]
   :factory        #(datastar-signals-middleware %)})

(defn datastar-render-multiplexer-middleware
  "Creates a middleware that adds the multiplexer to the request map.

  Adds the key :hifi.datastar/multiplexer to the request map with the value of the multiplexer"
  [{:keys [datastar-render-multiplexer_] :as config}]
  (assert datastar-render-multiplexer_)
  {:name ::datastar-render-multiplexer
   :wrap (fn wrap-datastar-render-multiplexer [handler]
           (fn [req]
             (handler (assoc req :hifi.datastar/multiplexer (force datastar-render-multiplexer_)))))})

(def DatastarRenderMultiplexerMiddlewareComponentData
  {:name                ::datastar-render-multiplexer
   :options-schema      nil
   :donut.system/config {:datastar-render-multiplexer_ [:donut.system/ref [:hifi/components :datastar-render-multiplexer]]}
   :factory             #(datastar-render-multiplexer-middleware %)})
