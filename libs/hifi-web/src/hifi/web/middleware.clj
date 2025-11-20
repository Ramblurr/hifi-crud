(ns hifi.web.middleware
  (:require
   [hifi.web.middleware.csrf :as csrf]
   [hifi.web.middleware.exception :as exception]
   [hifi.web.middleware.reverse-route :as reverse-route]
   [hifi.web.middleware.secheaders :as secheaders]
   [hifi.web.middleware.session :as session]
   [reitit.ring.middleware.multipart :as reitit.multipart]
   [reitit.ring.middleware.parameters :as reitit.params]))

(def ParametersMiddlewareComponentData
  {:name           :parse-raw-params
   :factory        (constantly reitit.params/parameters-middleware)
   :config-spec nil})

(def ParseMultipartMiddlewareComponentData
  {:name           :parse-multipart
   :factory        #(reitit.multipart/create-multipart-middleware %)
   :config-spec nil})

(defn middleware-component [{:keys [name factory config-spec :donut.system/config] :as _component-data}]
  {:donut.system/start  (fn [{:keys [:donut.system/config]}]
                          (factory config))
   :donut.system/config (merge {} config)
   :hifi/config-spec config-spec
   :hifi/config-key  name})

(def MiddlewareRegistryComponentGroup
  {:parse-raw-params            (middleware-component ParametersMiddlewareComponentData)
   :parse-multipart             (middleware-component ParseMultipartMiddlewareComponentData)
   :reverse-route               (middleware-component reverse-route/ReititReverseRouteMiddlewareComponentData)
   :exception                   (middleware-component exception/ExceptionMiddlewareComponentData)
   :exception-backstop          (middleware-component exception/ExceptionBackstopMiddlewareComponentData)
   ;; :datastar-signals            (middleware-component d*mw/DatastarSignalsMiddlewareComponentData)
   ;; :datastar-render-multicaster (middleware-component d*mw/DatastarRenderMulticasterMiddlewareComponentData)
   ;; :datastar-tab-state          (middleware-component d*mw/DatastarTabStateMiddlewareComponentData)
   :session-cookie              (middleware-component session/SessionMiddlewareComponentData)
   :csrf-protection             (middleware-component csrf/CSRFProtectionMiddlewareComponentData)
   :security-headers            (middleware-component secheaders/SecurityHeadersMiddlewareComponentData)})

(def hypermedia-chain
  [:parse-raw-params
   :reverse-route
   :exception
   :parse-multipart
   ;; :datastar-signals
   ;; :datastar-render-multicaster
   ;; :datastar-tab-state
   :session-cookie
   :csrf-protection
   :security-headers])
