(ns hifi.system.middleware
  (:require

   [hifi.datastar.middleware :as d*mw]
   [hifi.system.middleware.remote-addr :as remote-addr]
   [hifi.system.middleware.reverse-route :as reverse-route]
   [hifi.system.middleware.csrf :as csrf]
   [hifi.system.middleware.session :as session]
   [hifi.system.middleware.secheaders :as secheaders]
   [hifi.system.middleware.exception :as exception]
   [reitit.ring.middleware.parameters :as reitit.params]
   [reitit.ring.middleware.multipart :as reitit.multipart]))

(def ParametersMiddlewareComponentData
  {:name           :parse-raw-params
   :factory        (constantly reitit.params/parameters-middleware)
   :options-schema nil})

(def ParseMultipartMiddlewareComponentData
  {:name           :parse-multipart
   :factory        #(reitit.multipart/create-multipart-middleware %)
   :options-schema nil})

(defn middleware-component [{:keys [_name factory _options-schema :donut.system/config] :as _component-data}]
  {:donut.system/start  (fn [{:keys [:donut.system/config]}]
                          (factory config))
   :donut.system/config (merge {} config)})

(def MiddlewareRegistryComponentGroup
  {:parse-raw-params            (middleware-component ParametersMiddlewareComponentData)
   :parse-multipart             (middleware-component ParseMultipartMiddlewareComponentData)
   :reverse-route               (middleware-component reverse-route/ReititReverseRouteMiddlewareComponentData)
   :exception                   (middleware-component exception/ExceptionMiddlewareComponentData)
   :exception-backstop          (middleware-component exception/ExceptionBackstopMiddlewareComponentData)
   :datastar-signals            (middleware-component d*mw/DatastarSignalsMiddlewareComponentData)
   :datastar-render-multicaster (middleware-component d*mw/DatastarRenderMulticasterMiddlewareComponentData)
   :datastar-tab-state          (middleware-component d*mw/DatastarTabStateMiddlewareComponentData)
   :session-cookie              (middleware-component session/SessionMiddlewareComponentData)
   :csrf-protection             (middleware-component csrf/CSRFProtectionMiddlewareComponentData)
   :security-headers            (middleware-component secheaders/SecurityHeadersMiddlewareComponentData)
   ;; :wrap-http-kit-set-real-remote-address (middleware-component remote-addr/HttpKitSetRealRemoteAddressComponentData)
   ;; :wrap-parse-x-forwarded-for            (middleware-component remote-addr/ParseXForwardedForComponentData)
   })

(def hypermedia-chain
  [;; :wrap-http-kit-set-real-remote-address
   ;; :wrap-parse-x-forwarded-for
   :parse-raw-params
   :reverse-route
   :exception
   :parse-multipart
   :datastar-signals
   :datastar-render-multicaster
   :datastar-tab-state
   :session-cookie
   :csrf-protection
   :security-headers])
