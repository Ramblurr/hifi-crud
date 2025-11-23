(ns {{top/ns}}.{{main/ns}}
  (:require
   [hifi.web :as web]
   [hifi.config :as config]
   [hifi.assets :as assets]
   [hifi.html :as html]
   [hifi.core :as h]))

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
                               [::html/stylesheet-link {:href "css/app.css"}]]
                              [:body
                               [:h1 "Hello w World"]]]
                             nil))})

(h/defroutes routes
  ["" {:middleware hypermedia-chain}
   ["/" {:get  {:handler #'hello-world}}]
   ["/error" {:get  {:handler (fn [_] (throw (ex-info "Uhoh" {})))}}]])

(h/defplugin app
  "My application"
  {:hifi/routes (web/route-group routes)})
