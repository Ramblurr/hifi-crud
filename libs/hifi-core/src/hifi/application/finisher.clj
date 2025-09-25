(ns hifi.application.finisher
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [donut.system :as ds]
   [hifi.config :as config]
   [hifi.core :as h :refer [defplugin*]]
   [hifi.html :as html]
   [hifi.http :as hifi.http]
   [reitit.core :as r]))

(def _clojure-version
  (let [{:keys [major minor incremental]} *clojure-version*]
    (str major "." minor "." incremental)))

(defn welcome-page []
  (->
   (slurp (io/resource "hifi/application/welcome.html"))
   (str/replace #"@V-HIFI@" "dev")
   (str/replace #"@V-CLOJURE@" _clojure-version)
   (str/replace #"@V-JAVA@" (System/getProperty "java.version"))))

(defn route-data [routes]
  (->> routes
       (map (fn [[path data]]
              (let [route-name (:name data)
                    {:keys [ns line]} (:hifi/annotation data)
                    other-keys (dissoc data :name :hifi/annotation :middleware)]
                {:route-name route-name
                 :path path
                 :ns ns
                 :line line
                 :middleware (:middleware data)
                 :route-data (keys other-keys)})))
       (sort-by :path)))

(defn route-list [req]
  (html/->str
   (let [registry (:reitit.middleware/registry (r/options (:reitit.core/router req)))
         data (route-data (r/routes (:reitit.core/router req)))]
     [:div
      [:style "
        .route-table { border-collapse: collapse; width: 100%; }
        .route-table th, .route-table td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        .route-table thead tr { background-color: #f2f2f2; }
        .route-table .mono { font-family: monospace; }
        .route-table .route-data { color: #0066cc; font-weight: 500; }
        .route-table .mw-item { display: inline-block; margin-right: 8px; }
        .route-table .mw-link { color: #0066cc; text-decoration: none; }
        .route-table .mw-link:hover { text-decoration: underline; }
        .route-table .mw-name { color: #666; }"]
      [:h2 "Application Routes"]
      [:table.route-table
       [:thead
        [:tr
         [:th "Path"]
         [:th "Route Name"]
         [:th "Data"]
         [:th "Middleware"]
         [:th "Namespace"]
         [:th "Line"]]]
       [:tbody
        (for [{:keys [path route-name ns line route-data middleware]} data]
          [:tr
           [:td.mono path]
           [:td (when route-name (str route-name))]
           [:td.route-data (when (seq route-data)
                             (str/join ", " (map str route-data)))]
           [:td
            (when (seq middleware)

              (for [mw-key middleware
                    :let [mw-info (get registry mw-key)
                          mw-name (str mw-key)
                          mw-doc (:doc mw-info)
                          mw-link (:doc-link mw-info)]]
                [:span.mw-item {:key mw-key}
                 (if mw-link
                   [:a.mw-link {:href mw-link
                                :target "_blank"
                                :title mw-doc}
                    mw-name]
                   [:span.mw-name {:title mw-doc}
                    mw-name])]))]
           [:td.mono (when ns (str ns))]
           [:td (when line (str line))]])]]])))

(def welcome-routes ["/" {:name ::welcome
                          :get (fn [_req]
                                 {:status 200
                                  :headers {"Content-Type" "text/html"}
                                  :body (welcome-page)})}])

(def internal-routes ["/routes" {:name ::internal-routes
                                 :get (fn [req]
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
