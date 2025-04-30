;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.system.spec
  (:require
   [hifi.error.iface :as pe]
   [reitit.middleware :as reitit.middleware]
   [hifi.system.middleware :as default.middleware]))

(def IntoMiddleware
  [:fn #(satisfies? reitit.middleware/IntoMiddleware %)])

(defn arity
  "Returns the arities (a vector of ints) of:
    - anonymous functions like `#()` and `(fn [])`.
    - defined functions like `map` or `+`.
    - macros, by passing a var like `#'->`.

  Returns `[:variadic]` if the function/macro is variadic.
  Otherwise returns nil"
  [f]
  (let [func      (if (var? f) @f f)
        methods   (->> func
                       class
                       .getDeclaredMethods
                       (map (fn [^java.lang.reflect.Method m]
                              (vector (.getName m)
                                      (count (.getParameterTypes m))))))
        var-args? (some #(-> % first #{"getRequiredArity"})
                        methods)
        arities   (->> methods
                       (filter (comp #{"invoke"} first))
                       (map second)
                       (sort))]
    (cond
      (keyword? f)     nil
      var-args?        [:variadic]
      (empty? arities) nil
      :else            (if (and (var? f) (-> f meta :macro))
                         (mapv #(- % 2) arities) ;; substract implicit &form and &env arguments
                         (into [] arities)))))

(defn zero-arity? [f]
  (= 0 (first (arity f))))

(def ZeroArityFn [:fn {:error/message "should be a 0-arity function"} zero-arity?])

(def Var
  [:fn {:name ::var :error/message "clojure.lang.Var of a function"}
   #(var? %)])

(def HTTPKitServerOptions
  ;; ref https://github.com/http-kit/http-kit/blob/master/src/org/httpkit/server.clj
  [:map {:error/message "should be a map of HTTPServerOptions"}
   [:worker-pool {:doc "java.util.concurrent.ExecutorService or delay to use for handling requests" :optional true} :any]
   [:max-body {:doc "Max HTTP body size in bytes" :default (* 8 1024 1024) :optional true} pos-int?]
   [:max-ws {:doc "Max WebSocket message size in bytes" :default (* 4 1024 1024) :optional true} pos-int?]
   [:max-line {:doc "Max HTTP header line size in bytes" :default (* 8 1024) :optional true} pos-int?]
   [:proxy-protocol {:doc "Proxy protocol e/o #{:disable :enable :optional}" :optional true}
    [:enum :disable :enable :optional]]
   [:server-header {:doc "The \"Server\" header, disabled if nil" :default "http-kit" :optional true}
    [:maybe :string]]
   [:error-logger {:doc "Function to log errors (fn [msg ex])" :optional true} fn?]
   [:warn-logger {:doc "Function to log warnings (fn [msg ex])" :optional true} fn?]
   [:event-logger {:doc "Function to log events (fn [ev-name])" :optional true} fn?]
   [:event-names {:doc "Map of http-kit event names to loggable event names" :optional true}
    [:map-of :keyword :keyword]]
   [:address-finder {:doc "Function that returns java.net.SocketAddress for UDS support" :optional true} fn?]
   [:channel-factory {:doc "Function that takes java.net.SocketAddress and returns java.nio.channels.SocketChannel for UDS support" :optional true} fn?]])

(def HTTPServerOptions
  [:map {:error/message "should be a map of HTTPServerOptions"}
   [:port {:doc "Which port to listen on for incoming requests"} pos-int?]
   [:host {:doc "Which IP to bind"} :string]
   [:http-kit {:optional true} HTTPKitServerOptions]])

(def ResourceHandlerOptions
  [:map
   [:parameter         {:doc "Optional name of the wildcard parameter, defaults to unnamed keyword `:`" :optional true} :keyword]
   [:root              {:doc "The resource root, a path to the root directory in your resources/ (or elsewhere on the classpath)" :default "public" :optional true} [:string {:min 1}]]
   [:path              {:doc "Path to mount the handler to. Required when mounted outside of a router, does not work inside a router." :default "/" :optional true} [:string {:min 1}]]
   [:loader            {:doc "The class loader to resolve the resources" :optional true} :any]
   [:index-files       {:doc "A vector of index-files to look in a resource directory" :default ["index.html"] :optional true} [:vector :string]]
   [:not-found-handler {:doc "Optional handler function to use if the requested resource is missing (404 Not Found)" :optional true} :any]])

(def DefaultHandlerOptions
  [:map
   [:not-found {:doc "404, when no route matches" :optional true} fn?]
   [:method-not-allowed {:doc "405, when no method matches" :optional true} fn?]
   [:not-acceptable {:doc "406, when handler returns nil" :optional true} fn?]])

(def RingHandlerOptions
  [:map {:error/message "should be a valid hifi system ring handler component options map"}
   [:reload-per-request? {:doc "Reload the routes and handler on every request, useful for most (but not all) dev situations. You must pass the `routes` as a function var (i.e, `:routes #routes`" :default false} :boolean]
   [:resource-handler-opts {:doc      "Options for the resource handler"
                            :default  {:path "/"}
                            :optional true} ResourceHandlerOptions]
   [:default-handler-opts {:doc      "Options for the default handler"
                           :optional true} DefaultHandlerOptions]
   [:handler-opts
    {:doc           "The options map passed to the ring-handler function."
     :error/message "should be a valid ring handler component options map"
     :default       {}}
    :map]])

(def CreateHandlerOptsSchema
  [:map
   [:reload-per-request? :boolean]
   [:routes ZeroArityFn]
   [:handler-opts map?]
   [:router-options map?]
   [:resource-handler-opts :map]
   [:middleware-top-level [:vector IntoMiddleware]]])

(def RouterOptionsOptions
  [:map {:error/message "should be a map of options passed to reitit's router function after the route data"}
   [:route-data {:doc "Initial top-level route data" :default {}} :any]
   [:print-context-diffs? {:default false} :boolean]
   [:middleware-transformers {:doc     "A vector of middleware chain transformers"
                              :default []} [:vector fn?]]])

(def ReititRoutesLike [:multi {:dispatch (fn [v]
                                           (cond (fn? v)     :fn
                                                 (vector? v) :vector
                                                 (var? v)    :var))}

                       [:vector [:vector {:min 1} :any]]
                       [:fn ZeroArityFn]
                       [:var [:fn {:error/message "should be a var of a function returning or vector of reitit routes"} (fn [v] (fn? (var-get v)))]]])

(def MiddlewareRegistryOptionsOptions
  [:map-of {:error/message "should be a map of middleware name keywords to options map for them"}
   :keyword :any])

(def MiddlewareRegistryOptions
  [:map-of {:error/message "should be a map of middleware name keywords to IntoMiddleware"}
   :keyword IntoMiddleware])

(def MiddlewareOptions
  [:map
   [:default-middleware-registry-fn {:doc     "A function that accepts middleware-opts and returns the default middleware registry. "
                                     :default default.middleware/default-middleware-registry} fn?]
   [:registry-fn {:doc      "A function that accepts middleware-opts and returns a middleware registry to be merged with the default middleware registry"
                  :optional true}
    fn?
    ;; MiddlewareRegistryOptions
    ]
   [:opts {:doc     "A map of middleware name keywords to maps of options for the respective middleware"
           :default {}} MiddlewareRegistryOptionsOptions]])

(def RoutesKey [:routes {:doc           "Route definitions using reitit syntax. Can be the routes themselves or a fn/0 that returns them."
                         :error/message "should be a vector of reitit routes or zero-arity function that returns the routes"}
                ReititRoutesLike])
(def ProfileEnum [:enum :dev :test :prod])
(def HifiComponentOptionsSchema
  [:map {:name ::hifi-component-options}
   RoutesKey
   [:profile {:default :dev} ProfileEnum]
   [:http-server HTTPServerOptions]
   [:middleware {:default {}} MiddlewareOptions]
   [:ring-handler {:default {}} RingHandlerOptions]
   [:router-options {:default {}} RouterOptionsOptions]])

(def system-opts->component-opts-path
  {:host                 [:http-server :host]
   :port                 [:http-server :port]
   :debug-errors?        [:middleware :opts :exception :debug-errors?]
   :middleware           [:middleware]
   :reload-per-request?  [:ring-handler :reload-per-request?]
   :print-context-diffs? [:router-options :print-context-diffs?]
   :routes               [:routes]})

(def HifiSystemOptionsSchema
  [:map {:name ::hifi-system-options}
   RoutesKey
   [:profile {:optional true} ProfileEnum]
   [:debug-errors? {:optional true} :boolean]
   [:print-context-diffs? {:optional true} :boolean]
   [:reload-per-request? {:optional true} :boolean]
   [:middleware {:optional true} MiddlewareOptions]
   [:port {:optional true} :int]
   [:host {:default "127.0.0.1"} :string]
   [:component-opts {:optional true} HifiComponentOptionsSchema]])

(defn system-opts->component-opts [raw-opts]
  (let [opts (pe/coerce! HifiSystemOptionsSchema (dissoc raw-opts :component-opts))]
    (->> system-opts->component-opts-path
         (map (fn [[k path]]
                (assoc-in {} path (get opts k)))))))
