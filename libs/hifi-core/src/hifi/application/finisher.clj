(ns hifi.application.finisher
  (:require
   [reitit.core :as r]
   [clojure.string :as str]
   [hifi.config :as config]
   [clojure.java.io :as io]
   [donut.system :as ds]
   [hifi.core :as h :refer [defplugin*]]
   [hifi.http :as hifi.http]))

(def _clojure-version
  (let [{:keys [major minor incremental]} *clojure-version*]
    (str major "." minor "." incremental)))

(defn welcome-page []
  (->
   (slurp (io/resource "hifi/application/welcome.html"))
   (str/replace #"@V-HIFI@" "dev")
   (str/replace #"@V-CLOJURE@" _clojure-version)
   (str/replace #"@V-JAVA@" (System/getProperty "java.version"))))

(defn route-list [req]
  (tap> (r/routes (:reitit.core/router req)))
  "wutwut")

(def welcome-routes ["/" {:name ::welcome
                          :get (fn [_req]
                                 {:status 200
                                  :headers {"Content-Type" "text/html"}
                                  :body (welcome-page)})}])

(def internal-routes ["/routes" {:name ::internal-routes
                                 :get  (fn [req]
                                         {:status 200
                                          :headers {"Content-Type" "text/html"}
                                          :body (route-list req)})}])

(defn maybe-add-welcome [system]
  (if (empty? (-> system ::ds/defs :hifi/routes))
    (assoc-in system [::ds/defs :hifi/routes]
              (hifi.http/route-group {:routes welcome-routes
                                      :route-name ::app}))

    system))
(defn maybe-add-internal [system]
  (if (config/dev?)
    (assoc-in system [::ds/defs :hifi/routes ::internal-routes] (hifi.http/route-component internal-routes {:route-name ::internal-routes :path-prefix "/hifi"}))
    system))

(defplugin* hifi-finisher-plugin
  "Adds finishing touches"
  {h/system-update (fn [system]
                     (-> system
                         (maybe-add-welcome)
                         (maybe-add-internal)))})
