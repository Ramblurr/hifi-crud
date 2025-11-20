(ns hifi.web.middleware.reverse-route
  (:require [reitit.core :as r]))

(defn url-for
  "Return a url string given a route name and optional arg and query params."
  ([req name-or-path]
   (url-for req name-or-path nil nil))
  ([req name-or-path args]
   (url-for req name-or-path args nil))
  ([req name-or-path args query-params]
   (if (string? name-or-path)
     name-or-path
     (-> (::r/router req)
         (r/match-by-name name-or-path args)
         (r/match->path query-params)))))

(defn wrap-reverse-route
  "Middleware that adds a `:url-for` function to the request map"
  [handler]
  (fn [req]
    (let [url-for (partial url-for req)]
      (handler (assoc req :url-for url-for)))))

(def ReititReverseRouteMiddlewareComponentData
  {:name           :url-for
   :factory        (constantly {:wrap wrap-reverse-route})
   :config-spec nil})
