;; Copyright © 2025 Anders Murphy
;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; Portions of this file are based on hyperlith code from @Anders
;; https://github.com/andersmurphy/hyperlith/
;; SPDX-License-Identifier: MIT
(ns hifi.system.middleware.secheaders
  "Middleware for setting default security headers"
  (:require
   [hifi.util.crypto :as crypto]
   [hifi.system.middleware.spec :as options]
   [medley.core :as medley]
   [clojure.string :as str]))

(defn csp->str [csp-data]
  (reduce
   (fn [acc [k v]] (str acc (name k) " " (str/join " " v) ";"))
   ""
   csp-data))

(def self "'self'")
(def none "'none'")
(def unsafe-inline "'unsafe-inline'")
(def unsafe-eval "'unsafe-eval'")

(defn nonce []
  (crypto/rand-string 16))

(defn add-nonce [csp key nonce]
  (update csp key conj (format "'nonce-%s'" nonce)))

(defn add-nonces [csp keys nonce]
  (reduce #(add-nonce %1 %2 nonce) csp keys))

(defn build-headers [{:keys [hsts?
                             content-security-policy-raw
                             content-security-policy
                             x-permitted-cross-domain-policies
                             referrer-policy
                             x-content-type-options
                             enable-csp-nonce
                             x-frame-options]}
                     nonce-val]
  (medley/remove-vals
   #(or (nil? %) (str/blank? %))
   {"Strict-Transport-Security"         (when hsts? "max-age=63072000;includeSubDomains;preload")
    "Content-Security-Policy"           (or content-security-policy-raw
                                            (cond-> content-security-policy
                                              enable-csp-nonce (add-nonces enable-csp-nonce nonce-val)
                                              true             (csp->str)))
    "X-Permitted-Cross-Domain-Policies" x-permitted-cross-domain-policies
    "Referrer-Policy"                   referrer-policy
    "X-Content-Type-Options"            x-content-type-options
    "X-Frame-Options"                   x-frame-options}))

(defn ->secheaders-middleware-opts [opts]
  (options/valid! "security-headers-middleware"
                  options/SecHeadersMiddlewareOptions
                  (options/coerce options/SecHeadersMiddlewareOptions (or opts {}))))

(defn security-headers-middleware
  "Default security headers to set in the response."
  [opts]
  {:name           ::security-headers
   :options-schema options/SecHeadersMiddlewareOptions
   :wrap           (fn wrap-security-headers [handler]
                     (fn [request]
                       (let [nonce-val   (nonce)
                             sec-headers (build-headers (->secheaders-middleware-opts opts) nonce-val)
                             response    (handler (assoc request
                                                         ;; We add the security headers to the request in case a handler wants to inspect it
                                                         :security-headers sec-headers
                                                         :nonce nonce-val))]
                         (update response :headers #(merge sec-headers %)))))})

(def SecurityHeadersMiddlewareComponentData
  {:name           ::security-headers
   :options-schema options/SecHeadersMiddlewareOptions
   :factory        #(security-headers-middleware %)})

(comment
  (build-headers (->secheaders-middleware-opts nil))

  (build-headers (->secheaders-middleware-opts {:x-frame-options ""}))
  ;;
  )
