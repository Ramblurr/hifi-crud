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
    - read-json - an arity/1 function accepting the raw signal data and returning the parsed json as edn. defaults to a charred parese fn"
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
                                   :submitted-csrf-token (:csrf-token signals-edn))))
                 (handler req))
               (handler req))))})
