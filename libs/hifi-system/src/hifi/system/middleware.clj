(ns hifi.system.middleware
  (:require
   [hifi.datastar.middleware :as d*mw]
   [hifi.system.middleware.csrf :as csrf]
   [hifi.system.middleware.session :as session]
   [hifi.system.middleware.secheaders :as secheaders]
   [hifi.system.middleware.exception :as exception]
   [reitit.ring.middleware.parameters :as reitit.params]
   [reitit.ring.middleware.multipart :as reitit.multipart]))

;; -----------------------------------------------------------------------------
;; Default Middleware Registry

(defn default-middleware-registry [config opts]
  {:parse-raw-params reitit.params/parameters-middleware
   :parse-multipart  (reitit.multipart/create-multipart-middleware (:parse-multipart opts))

   :exception                   (exception/exception-middleware (:exception opts))
   :exception-backstop          (exception/exception-backstop-middleware (:exception-backstop opts))
   :datastar-signals            (d*mw/datastar-signals-middleware (:datastar-signals opts))
   :datastar-render-multiplexer (d*mw/datastar-render-multiplexer-middleware (:datastar-render-multiplexer config))
   :session-cookie              (session/session-middleware (:session-cookie opts))
   :csrf-protection             (csrf/csrf-middleware (:csrf-protection opts))
   :security-headers            (secheaders/security-headers-middleware (:security-headers opts))})

(def hypermedia-chain
  [:parse-raw-params
   :exception
   :parse-multipart
   :datastar-signals
   :datastar-render-multiplexer
   :session-cookie
   :csrf-protection
   :security-headers])
