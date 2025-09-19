(ns hifi.datastar.middleware
  (:require
   [hifi.datastar.tab-state :as tab-state]
   [charred.api :as charred]
   [starfederation.datastar.clojure.api :as d*]))

;; -----------------------------------------------------------------------------
;; Datastar Signals Middleware

(def default-read-json (charred/parse-json-fn {:async? false :bufsize 1024 :key-fn keyword}))

(defn datastar-signals-middleware
  "Middleware to parse and extract datastar signals from the request

   This middleware adds these keys to the request map:
    :hifi.datastar/signals - a map of datastar signals
    :hifi.datastar/tab-id - the tab-id sent from the client
    :hifi/submitted-csrf-token - the csrf token sent from the client

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
                                   :hifi.datastar/signals signals-edn
                                   :hifi.datastar/tab-id (:tab-id signals-edn)
                                   :hifi/submitted-csrf-token (:csrf signals-edn))))
                 (handler req))
               (handler req))))})

(def DatastarSignalsMiddlewareComponentData
  {:name           ::datastar-signals
   :options-schema [:map
                    [:read-json
                     {:doc "An arity/1 function accepting the raw signal data and returning the parsed json as edn. defaults to a charred parese fn"}
                     (fn? default-read-json)]]
   :factory        #(datastar-signals-middleware %)})

;; -----------------------------------------------------------------------------
;; Datastar Multicaster Middleware

(defn datastar-render-multicaster-middleware
  "Creates a middleware that adds the multicaster to the request map.

  Adds the key :hifi.datastar/multicaster to the request map with the value of the multicaster"
  [{:keys [datastar-render-multicaster_] :as _config}]
  (assert datastar-render-multicaster_)
  {:name ::datastar-render-multicaster
   :wrap (fn wrap-datastar-render-multicaster [handler]
           (fn [req]
             (handler (assoc req :hifi.datastar/multicaster (force datastar-render-multicaster_)))))})

(def DatastarRenderMulticasterMiddlewareComponentData
  {:name                ::datastar-render-multicaster
   :options-schema      nil
   :donut.system/config {:datastar-render-multicaster_ [:donut.system/ref [:hifi/components :datastar-render-multicaster]]}
   :factory             #(datastar-render-multicaster-middleware %)})

;; -----------------------------------------------------------------------------
;; Datastar Tab State Middleware

(defn datastar-tab-state-middleware
  "Creates a middleware that adds :hifi.datastar/tab-state-store and tab-state to the request map."
  [{:keys [tab-state] :as _config}]
  (let [!tab-state-store (:!tab-state-store tab-state)]
    (assert !tab-state-store)
    {:name ::datastar-tab-state
     :wrap (fn wrap-datastar-tab-state [handler]
             (fn [{:keys [:hifi.datastar/tab-id] :as req}]
               (handler
                (cond-> req
                  (some? tab-state) (assoc :hifi.datastar/tab-state-store !tab-state-store)
                  (some? tab-id)    (assoc :hifi.datastar/tab-state (tab-state/tab-state! !tab-state-store tab-id))))))}))

(def DatastarTabStateMiddlewareComponentData
  {:name                ::datastar-tab-state
   :options-schema      nil
   :donut.system/config {:tab-state [:donut.system/ref [:hifi/components :tab-state]]}
   :factory             #(datastar-tab-state-middleware %)})
