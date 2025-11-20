(ns hello.routes
  (:require
   [hifi.core :as h]
   [hifi.html :as html]
   [hifi.system.middleware :as hifi.mw]))

(def hypermedia-chain
  [:parse-raw-params
   :reverse-route
   :exception
   :parse-multipart
   :session-cookie
   :csrf-protection
   :security-headers])

(defn hello-world [req]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (:html (html/render req
                             [html/doctype
                              [:head
                               [::html/stylesheet-link {:href "css/hello.css"}]]
                              [:body
                               [:h1 "Hello w World"]]]
                             nil))})

(h/defroutes app
  ["" {:middleware hypermedia-chain}
   ["/" {:get  {:handler #'hello-world}}]
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
