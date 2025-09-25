(ns hello.routes
  (:require
   [hifi.system.middleware :as hifi.mw]))

(def routes
  ["" {:middleware hifi.mw/hypermedia-chain}
   ["/" {:get  {:handler (fn [_] {:status 200 :body "Hello world"})}}]
   ["/error" {:get  {:handler (fn [_] (throw (ex-info "Uhoh" {})))}}]])
