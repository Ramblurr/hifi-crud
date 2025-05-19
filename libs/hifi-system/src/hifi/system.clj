(ns hifi.system
  (:require [clojure.string :as str]
            [hifi.logging :as logging]
            [hifi.datastar.tab-state :as tab-state]
            [hifi.datastar.system :as datastar]
            [hifi.env :as env]
            [hifi.system.middleware :as middleware]
            [org.httpkit.server :as hk-server]
            [com.fulcrologic.guardrails.malli.core :refer [=> >defn-]]
            [donut.system :as ds]
            [donut.system.plugin :as dsp]
            [donut.system.validation :as dsv]
            [medley.core :as medley]
            [hifi.error.iface :as pe]
            [hifi.system.options-plugin :as pop]
            [hifi.system.spec :as spec]
            [reitit.ring.middleware.dev :as reitit.ring.middleware.dev]
            [reitit.ring :as reitit.ring])
  (:import
   [clojure.lang ExceptionInfo]))

(defn start-hk [handler {:keys [port host] :as opts}]
  (let [hk-opts (:http-kit opts)]
    (hk-server/run-server handler
                          (-> hk-opts
                              (assoc  :legacy-return-value? false)
                              (assoc :ip host)
                              (assoc :port port)))))

(defn stop-hk [instance]
  (when instance
    (hk-server/server-stop! instance)))

(def HTTPServerComponent
  "A donut.system component for starting a ring compatible http server. This one uses http-kit under the hood.

  Config:

    - :ring-handler - (local ref) a ring handler function
    - :hifi/options - see [[spec/HTTPServerOptions]]
  "
  {:donut.system/start  (fn http-server-component-start [{{:keys [handler :hifi/options]} :donut.system/config}]
                          (start-hk handler options))
   :donut.system/stop   (fn http-server-component-stop [{:donut.system/keys [instance]}]
                          (stop-hk instance))
   :hifi/options-schema spec/HTTPServerOptions
   :hifi/options-ref    [:hifi/components :options :http-server]
   :donut.system/config {:handler [:donut.system/local-ref [:ring-handler]]}})

(defn coerce-to-fn
  "Coerce `maybe-fn` to a 0-arity function.
  If `maybe-fn` is a fn or a var pointing to a fn, then `maybe-fn` is returned,
  if `maybe-fn` is nil, returns nil
  else returns a function that returns `maybe-fn`"
  [maybe-fn]
  (cond
    (nil? maybe-fn) nil
    (var? maybe-fn) (if (fn? (var-get maybe-fn))
                      maybe-fn
                      (fn [] (var-get maybe-fn)))
    (fn? maybe-fn)  maybe-fn
    :else           (fn [] maybe-fn)))

(defonce ^{:private true
           :doc     "When :reload-per-request? is true, and we are reloading the router on every request, then we cannot fetch
   the route data off the handler like we ususally do with (-> handler meta :reitit.core/router).
   Introspecting the actual compiled routes at runtime is very useful!
   .. but so is reloading the routes on every request..
   So we use this atom that will contain the router for the last request.
   You can fetch it out of the system map with
   ```clojure
   @(-> (repl/get-in [:hifi/components :ring-handler ]) meta :hifi/router-last-request)
   ```"}
  -dev-routes-last-request
  (atom nil))

(>defn- create-handler-fn
  "Returns an arity 0 function that creates a ring handler"
  [{:keys [resource-handler-opts reload-per-request? router-options routes handler-opts
           default-handler-opts middleware-top-level]}]
  [spec/CreateHandlerOptsSchema => fn?]
  (fn create-handler-inner []
    (let [router (reitit.ring/router (routes) router-options)]
      (when reload-per-request?
        (reset! -dev-routes-last-request router))
      (reitit.ring/ring-handler
       router
       (reitit.ring/routes
        (reitit.ring/create-resource-handler resource-handler-opts)
        (reitit.ring/create-default-handler default-handler-opts))
       (merge {:middleware middleware-top-level}
              handler-opts)))))

(defn- prepare-create-handler-opts
  "Given our RingHandlerComponent config, builds and validates a map of options used to construct the ring handler"
  [config]
  (let [routes              (-> config :routes)
        reload-per-request? (-> config :hifi/options :reload-per-request?)]
    (when (and reload-per-request?
               (or (not (var? routes))
                   (not (fn? (var-get routes)))))
      (let [msg "WARNING: For :reload-per-request? to work you need to pass a function var for routes."]
        (tap> msg)
        (println msg)))
    (pe/coerce! spec/CreateHandlerOptsSchema {:reload-per-request?   reload-per-request?
                                              :routes                (coerce-to-fn routes)
                                              :router-options        (-> config :router-options)
                                              :handler-opts          (-> config :hifi/options :handler-opts)
                                              :resource-handler-opts (-> config :hifi/options :resource-handler-opts)
                                              :default-handler-opts  (-> config :hifi/options :default-handler-opts)
                                              :middleware-top-level  (if-let [exception-backstop (-> config :middleware-registry :exception-backstop)]
                                                                       [exception-backstop]
                                                                       [])})))

(defn reloading-ring-handler
  "Returns a ring-handler that recreates the actual ring-handler for each request.
  Takes a 0-arity function that should return a valid ring-handler. Effectively creates
  an auto-reloading ring-handler, which is good for REPL-driven development.

  This implementation will render exceptions thrown by f."
  [f]
  (letfn [(strip-colors [s]
            (str/replace s #"\033\[[0-9;]*m" ""))
          (resolve-handler []
            (try
              [(f) nil]
              (catch Throwable t
                [nil (ex-info "Your handler/routes function threw an exception." {:f f} t)])))

          (error [^Throwable t]
            (.printStackTrace t)
            {:status  500
             :headers {"Content-Type" "text/plain"}
             :body    (strip-colors (str (.getMessage t) ":\n"
                                         (.getMessage (.getCause t)) "\n"))})]

    (fn
      ([request]
       (let [[handler ex] (resolve-handler)]
         (if ex (error ex)
             (handler request))))
      ([request respond raise]
       (let [[handler ex] (resolve-handler)]
         (if ex
           (respond (error ex))
           (handler request respond raise)))))))

(defn- create-reloading-ring-handler [create-handler]
  ;; call create-handler and discard the result
  ;; this is to trigger exceptions that may occur instead of waiting until the first request, this improves the DX
  (create-handler)
  (with-meta
    (reloading-ring-handler create-handler)
    {:hifi/router-last-request -dev-routes-last-request}))

(def RingHandlerComponent
  "A donut.system component for a ring handler function.

  Config:

    - :hifi/options - See RingHandlerOptions
    - :routes - the vector (of fn/0 returning) the reitit routes
    - :router-options - map of options passed to reitit's router"
  #::ds{:start               (fn ring-handler-component-start [{::ds/keys [config]}]
                               (let [create-handler (-> config (prepare-create-handler-opts) (create-handler-fn))]
                                 (if (-> config :hifi/options :reload-per-request?)
                                   (create-reloading-ring-handler create-handler)
                                   (create-handler))))
        :hifi/options-schema spec/RingHandlerOptions
        :hifi/options-ref    [:hifi/components :options :ring-handler]

        :config {:routes              [::ds/local-ref [:options :routes]]
                 :middleware-registry [::ds/ref [:hifi/middleware]]
                 :router-options      [::ds/local-ref [:router-options]]}})

(def RouterOptionsComponent
  "Constructs a router options map (to be passed as 2nd arg to the reitit router function)"
  #::ds{:start (fn router-options-component-start [{::ds/keys [config]}]
                 (let [middleware-registry            (:middleware-registry config)
                       {:keys [middleware-transformers
                               route-data
                               print-context-diffs?]} (:hifi/options config)]
                   {:reitit.middleware/registry  middleware-registry
                    :reitit.middleware/transform (if print-context-diffs?
                                                   (conj middleware-transformers reitit.ring.middleware.dev/print-request-diffs)
                                                   middleware-transformers)
                    :data                        route-data}))

        :hifi/options-schema spec/RouterOptionsOptions
        :hifi/options-ref    [:hifi/components :options :router-options]
        :config              {:middleware-registry [::ds/ref [:hifi/middleware]]}})

(defn xxx ([v]
           (tap> v)
           v)
  ([tag v]
   (tap> [tag v])
   v))

(defn coerce-opts [opts env]
  ;; There are 3 sources of options (in order of priority from highest to least)
  ;; 1. the "sugar" options passed in the top level opts map to hifi-system
  ;; 2. the :component-opts options
  ;; 3. the env.edn file
  ;;
  ;; Here we combine all three into our final HifiComponentOptionsSchema
  (pe/coerce! spec/HifiComponentOptionsSchema
              (apply medley/deep-merge
                     (:hifi/components env)
                     (:component-opts opts)
                     (spec/system-opts->component-opts opts))))

(defn merge-middleware-env [middleware-registry-component-group middleware-env]
  (reduce (fn [comp-group mw-key]
            (update-in comp-group [mw-key :donut.system/config]
                       merge
                       (get middleware-env mw-key)))
          middleware-registry-component-group (keys middleware-env)))

(defn hifi-system
  "Returns a complete donut.system map intialized with the hifi system opts"
  [opts]
  (let [env     (env/read-env)
        options (coerce-opts opts env)]
    {::ds/defs
     {:env             (dissoc env :hifi/components)
      :hifi/middleware (merge-middleware-env  middleware/MiddlewareRegistryComponentGroup (:hifi/middleware env))
      :hifi/components {:http-server                 HTTPServerComponent
                        :ring-handler                RingHandlerComponent
                        :router-options              RouterOptionsComponent
                        :datastar-render-multicaster datastar/DatastarRenderMulticasterComponent
                        :tab-state                   tab-state/TabStateComponent
                        :options                     options
                        :logging-console             logging/ConsoleLoggingComponent
                        :logging-tap                 logging/TelemereTapHandlerComponent}}
     ::ds/plugins [pop/options-plugin]}))

(defn hifi-plugin
  "Accept hifi system options and returns a donut.system plugin that will load hifi into your system."
  [opts]
  {::dsp/name ::hifi

   ::dsp/doc
   "The hifi plugin for donut system. Attaches the core hifi components to the system."

   ::dsp/system-update   (fn [system]
                           ;; plugins cannot load other plugins
                           ;; ref: https://github.com/donut-party/system/issues/40
                           ;; so as a workaround we manually apply our options validation plugin
                           (dsp/apply-plugin system pop/options-plugin))
   ::dsp/system-defaults (hifi-system opts)})

(defn start
  "A convenience function to start a hifi system.

  Takes a map of hifi options, and optionally a map of additional donut.system component definitions.

  Returns a donut system map."
  ([opts]
   (start opts {}))
  ([opts defs]
   (try
     (ds/start
      {::ds/defs    (or defs {})
       ::ds/plugins [(hifi-plugin opts) dsv/validation-plugin]})
     (catch ExceptionInfo e
       (ds/stop-failed-system e)
       (throw e)))))

(defn stop
  "Stop Hifi. Accepts the system map returned by start"
  [system]
  (ds/stop system))
