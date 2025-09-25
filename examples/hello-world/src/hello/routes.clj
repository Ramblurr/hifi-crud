(ns hello.routes
  (:require
   [hifi.core :as h]
   [hifi.system.middleware :as hifi.mw]))

(h/defroutes app
  ["" {:middleware hifi.mw/hypermedia-chain}
   ["/" {:get  {:handler (fn [_] {:status 200 :body "Hello world"})}}]
   ["/error" {:get  {:handler (fn [_] (throw (ex-info "Uhoh" {})))}}]])

(comment

  ;; =>

  (def app
    {:route-name ::app
     :routes
     ["" {:hifi/annotation {:ns hello.routes :line XX}}
      ["/" {:get  {:handler (fn [_] {:status 200 :body "Hello world"})
                   :hifi/annotation {:ns hello.routes :line XX}}}]
      ["/error" {:get  {:handler (fn [_] (throw (ex-info "Uhoh" {})))}
                 :hifi/annotation {:ns hello.routes :line XX}}]]}))
