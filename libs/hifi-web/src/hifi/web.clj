(ns hifi.web
  (:require
   [com.fulcrologic.guardrails.malli.core :refer [=> >defn]]
   [donut.system :as ds]
   [hifi.config :as config]
   [hifi.core :as h]
   [hifi.util.crypto :as crypto]
   [hifi.web.middleware :as middleware]
   [hifi.web.spec :as spec]
   [reitit.ring :as reitit.ring]
   [reitit.ring.middleware.dev :as reitit.ring.middleware.dev]))

(h/defcomponent HTTPKitServerComponent
  "HTTP server component that starts and manages a Ring-compatible HTTP server using http-kit.
   http-kit is lazily required."
  {::ds/start (fn http-server-component-start [{{:keys [handler port host http-kit] :as _opts} ::ds/config}]
                (let [res ((requiring-resolve 'org.httpkit.server/run-server) handler
                                                                              (merge {:legacy-return-value? false
                                                                                      :legacy-content-length? false
                                                                                      :legacy-unsafe-remote-addr? false
                                                                                      :ip host
                                                                                      :port port}
                                                                                     http-kit))]
                  (println (str "hifi started. visit http://" host ":" port))
                  res))
   ::ds/stop (fn http-server-component-stop [{::ds/keys [instance]}]
               (try
                 ((requiring-resolve 'org.httpkit.server/server-stop!) instance)
                 (catch Exception _)))

   :hifi/config-spec spec/HTTPServerOptions
   :hifi/config-key :hifi.web/server
   ::ds/config {:handler [::ds/local-ref [:hifi.web/root-handler]]}})

(h/defcomponent RootRingHandlerComponent
  "Root Ring handler component that creates the main HTTP request handler.

   This component combines a reitit router with default handlers to create a complete
   Ring handler function. It processes incoming HTTP requests by routing them through
   the configured routes and applying appropriate middleware transformations."
  {::ds/start (fn ring-handler-component-start [{::ds/keys [config]}]
                (let [{:keys [router handler-opts default-handler-opts]} config]
                  (reitit.ring/ring-handler
                   router
                   (reitit.ring/routes (reitit.ring/create-default-handler default-handler-opts))
                   handler-opts)))
   :hifi/config-spec spec/RingHandlerOptions
   :hifi/config-key :hifi.web/root-handler
   ::ds/config {:router [::ds/local-ref [:hifi.web/router]]}})

(h/defcomponent RouterOptionsComponent
  "Router options component that configures reitit router behavior.

   This component builds the options map passed to the reitit router constructor,
   including middleware registry, transformers, and route data. It enables
   middleware composition and optional request diff printing for development."
  {::ds/start (fn router-options-component-start [{::ds/keys [config]}]
                (let [{:keys [middleware-transformers middleware-registry route-data print-context-diffs?]} config]
                  {:reitit.middleware/registry middleware-registry
                   :reitit.middleware/transform (if print-context-diffs?
                                                  (conj middleware-transformers reitit.ring.middleware.dev/print-request-diffs)
                                                  middleware-transformers)
                   :data route-data}))
   :hifi/config-spec spec/RouterOptionsOptions
   :hifi/config-key :hifi.web/router-options
   ::ds/config {:middleware-registry [::ds/ref [:hifi/middleware]]}})

(h/defcomponent RouterComponent
  "Reitit router component that creates the main request router.

   This component constructs a reitit router from the configured routes and options.
   The router is responsible for matching incoming requests to appropriate handlers
   based on URL patterns and HTTP methods defined in the route definitions."
  {::ds/start (fn [{:keys [:donut.system/config]}]
                (reitit.ring/router
                 (into [] (vals (:routes config)))
                 (:router-options config)))
   ::ds/config {:routes [:donut.system/local-ref [:hifi.web/routes]]
                :router-options [:donut.system/local-ref [:hifi.web/router-options]]}})

(h/defcomponent RootKeyComponent
  "TODO"
  {::ds/start (fn [{::ds/keys [config]}]
                (crypto/key->hmac-sha256-keyspec (config/unmask! (:key config))))
   :hifi/config-spec [:map {:name ::root-key-component} [:key spec/Secret]]
   :hifi/config-key :hifi/root-key})

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
       [[:vector :any] RouteComponentDef => :map]
       {::ds/start route-component-start
        :hifi/config-spec nil
        :hifi/config-key nil
        ::ds/config {:routes routes
                     :route-name route-name
                     :path-prefix path-prefix
                     :route-data route-data}})

(defn route-group
  [& components]
  (reduce (fn [group {:keys [routes route-name] :as opts}]
            (assoc group route-name (route-component routes opts)))
          {} components))

(h/defplugin plugin
  "The default, and recommended, component for HTTP applications"
  {:hifi/web {:hifi.web/server HTTPKitServerComponent
              :hifi.web/root-handler RootRingHandlerComponent
              :hifi.web/router-options RouterOptionsComponent
              :hifi.web/router RouterComponent
              :hifi.web/routes [::ds/ref [:hifi/routes]]
              :hifi.web/root-key RootKeyComponent}
   :hifi/middleware middleware/MiddlewareRegistryComponentGroup})
