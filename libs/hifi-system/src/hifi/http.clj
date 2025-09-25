(ns hifi.http
  (:require
   [com.fulcrologic.guardrails.malli.core :refer [=> >defn]]
   [donut.system :as ds]
   [hifi.core :as h]
   [hifi.system.middleware :as middleware]
   [hifi.system.spec :as spec]
   [reitit.ring :as reitit.ring]
   [reitit.ring.middleware.dev :as reitit.ring.middleware.dev]))

(def HTTPServerComponent
  "A donut.system component for starting a ring compatible http server. This one uses http-kit under the hood.

  Config:
  TODO
  "
  {::ds/start  (fn http-server-component-start [{{:keys [handler port host http-kit] :as _opts} ::ds/config}]
                 ((requiring-resolve 'org.httpkit.server/run-server) handler
                                                                     (merge {:legacy-return-value?       false
                                                                             :legacy-content-length?     false
                                                                             :legacy-unsafe-remote-addr? false
                                                                             :ip                         host
                                                                             :port                       port}
                                                                            http-kit)))
   ::ds/stop   (fn http-server-component-stop [{::ds/keys [instance]}]
                 (try
                   ((requiring-resolve 'org.httpkit.server/server-stop!) instance)
                   (catch Exception _)))

   :hifi/config-spec    spec/HTTPServerOptions
   :hifi/config-key     :hifi.http/server
   ::ds/config {:handler [::ds/local-ref [:hifi.http/root-handler]]}})

(def RootRingHandlerComponent
  "A donut.system component for the root ring handler function.

  Config: TODO"
  {::ds/start        (fn ring-handler-component-start [{::ds/keys [config]}]
                       (let [{:keys [router handler-opts default-handler-opts]} config]
                         (reitit.ring/ring-handler
                          router
                          (reitit.ring/routes
                           (reitit.ring/create-default-handler default-handler-opts))
                          handler-opts)))

   :hifi/config-spec spec/RingHandlerOptions
   :hifi/config-key  :hifi.http/root-handler
   ::ds/config       {:router              [::ds/local-ref [:hifi.http/router]]}})

(def RouterOptionsComponent
  "Constructs a router options map (to be passed as 2nd arg to the reitit router function)"
  {::ds/start        (fn router-options-component-start [{::ds/keys [config]}]
                       (let [{:keys [middleware-transformers middleware-registry route-data print-context-diffs?]} config]
                         {:reitit.middleware/registry  middleware-registry
                          :reitit.middleware/transform (if print-context-diffs?
                                                         (conj middleware-transformers reitit.ring.middleware.dev/print-request-diffs)
                                                         middleware-transformers)
                          :data                        route-data}))
   :hifi/config-spec spec/RouterOptionsOptions
   :hifi/config-key  :hifi.http/router-options
   ::ds/config       {:middleware-registry [::ds/ref [:hifi/middleware]]}})

(def RouterComponent
  {::ds/start  (fn [{:keys [:donut.system/config]}]
                 (reitit.ring/router
                  (into [] (vals (:routes config)))
                  (:router-options config)))
   :hifi/config-spec nil
   :hifi/config-key nil
   ::ds/config {:routes      [:donut.system/local-ref [:hifi.http/routes]]
                :router-options [:donut.system/local-ref [:hifi.http/router-options]]}})

(defn route-component-start [{:keys [::ds/config]}]
  (let [{:keys [routes path-prefix _route-name route-data]} config]
    [path-prefix route-data routes]))

(def RouteComponentDef
  [:map {:name ::route-component}
   [:path-prefix {:optional true} :string]
   [:route-name :keyword]
   [:route-data {:optional true} :map]])

(>defn route-component
       [routes {:keys [route-name path-prefix route-data]
                :or {path-prefix ""
                     route-data {}}}]

       [:vector RouteComponentDef => :map]
       {::ds/start route-component-start
        :hifi/config-spec nil
        :hifi/config-key nil
        ::ds/config {:routes routes
                     :route-name route-name
                     :path-prefix path-prefix
                     :route-data route-data}})

(>defn route-group
       [& components]
       [[:* RouteComponentDef] => :map]
       (reduce (fn [group {:keys [routes route-name] :as opts}]
                 (assoc group route-name (route-component routes opts)))
               {} components))

(h/defplugin defaults
  "The default, and recommended, component for HTTP applications"
  {:hifi/http {:hifi.http/server         HTTPServerComponent
               :hifi.http/root-handler   RootRingHandlerComponent
               :hifi.http/router-options RouterOptionsComponent
               :hifi.http/router         RouterComponent
               :hifi.http/routes         [::ds/ref [:hifi/routes]]}
   :hifi/routes {}
   :hifi/middleware middleware/MiddlewareRegistryComponentGroup})
