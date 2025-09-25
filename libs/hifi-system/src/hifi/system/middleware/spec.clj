;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.system.middleware.spec
  (:require
   [hifi.config :as config]
   [malli.transform :as mt]
   [ring.middleware.cookies :as ring-cookies]
   [malli.core :as m]
   [clojure.string :as str]
   [malli.error :as me]
   [malli.util :as mu]))

(defn coerce [schema v]
  (m/decode schema v (mt/default-value-transformer {::mt/add-optional-keys true})))

(defn valid! [int-name schema v]
  (if (m/validate schema v)
    v
    (throw  (ex-info  (str "Invalid options passed to middleware " int-name)
                      {:schema  schema
                       :value   v
                       :explain (me/humanize (m/explain schema v))}))))

(def uri-path?
  "Predicate for validating URI paths.
   Valid: `/`, `/foo`, `/foo/bar`
   Invalid: ``, `foo`, `foo/bar`"
  (fn [x]
    (and (string? x) (str/starts-with? x "/"))))

(def Secret
  [:and
   [:fn {:error/message "should be a secret value"} config/secret?]
   [:fn {:error/message "should be a secret value that isn't nil"} config/secret-present?]])

(def NonBlankString
  [:and
   [:string {:min 1}]
   [:fn {:error/message "non-blank string"}
    (complement str/blank?)]])

(def UriPath
  (m/-simple-schema
   {:type            :uri-path
    :pred            uri-path?
    :type-properties {:error/message "Must be a valid URI path starting with /"
                      :decode/string str
                      :encode/string str}}))
(assert (m/into-schema? UriPath))

(def CookieInterval
  [:or
   [:fn
    #(satisfies? ring-cookies/CookieInterval %)]
   [:int]])

(def CookieDateTime
  [:or
   [:fn
    #(satisfies? ring-cookies/CookieDateTime %)]
   [:string]])

(def CookieAttrs
  (m/schema
   [:map {:name ::cookie-attributes}
    [:value NonBlankString]
    [:path {:optional true} UriPath]
    [:domain {:optional true} NonBlankString]
    [:max-age {:optional true} pos-int?]
    [:expires {:optional true} NonBlankString] ;; RFC 7231 date format
    [:secure {:optional true} :boolean]
    [:secure-prefix {:optional true} :boolean]
    [:host-prefix {:optional true} :boolean]
    [:http-only {:optional true} :boolean]
    [:same-site {:optional true} [:enum :strict :lax]]]))

(def CookieAttrsOption (mu/dissoc CookieAttrs :value))

#_(def PrettyExceptionsPageOptions
    (m/schema
     [:map {:name ::pretty-exceptions-page-options}
      [:app-namespaces
       {:optional true
        :doc      "Controls which namespaces show up on the pretty errors page as \"application\" frames. All frames from namespaces prefixed with the names in the list will be marked as application frames."}
       [:sequential {:error/message "should be a sequence of app namespaces symbols"} :symbol]]
      [:skip? {:doc      "Allows for skipping the pretty exceptions middleware for a request. Should be a predicate function that takes the request as its argument."
               :optional true}
       [:fn {:error/message "should be a predicate function"} fn?]]]))

(def ExceptionMiddlewareOptions
  (m/schema
   [:map {:name ::exception-middleware-options}
    [:debug-errors? {:doc     "When true uses hifi.system.middleware.errors functionality for debugging application failures."
                     :default false} :boolean]
    [:error-handlers {:doc      "TODO"
                      :optional true} [:map-of :any fn?]]
    #_[:pretty-exceptions-opts {:doc     "Options for the pretty exceptiosn page handler"
                                :default {}} PrettyExceptionsPageOptions]]))

(def ExceptionBackstopMiddlewareOptions
  (m/schema
   [:map {:name ::exception-backstop-middleware-options}
    [:report {:optional true
              :doc      "A side-effecting function that takes [exception request] for logging/reporting. Defaults to a function that uses tap> to report the error. The return value is discarded."}
     [:fn fn?]]]))

(def CSRFProtectionMiddlewareOptions
  (m/schema
   [:map {:name ::csrf-protection-middleware-options}
    [:csrf-secret {:doc "A randomly generated random secret used to sign the CSRF token."} Secret]
    [:cookie-name {:default "csrf" :doc "Name of the cookie holding the csrf double-submit token."} NonBlankString]
    [:cookie-attrs {:default {:same-site :lax :secure true :path "/" :host-prefix true}
                    :doc     "Map of attributes for the csrf cookie."} CookieAttrsOption]]))

(def SessionMiddlewareOptions
  (m/schema
   [:map {:name ::session-middleware-options}
    [:cookie-name {:default "sid" :doc "Name of the cookie holding the session key."} NonBlankString]
    [:cookie-attrs {:default {:same-site :lax :http-only true :secure true :path "/" :host-prefix true}
                    :doc     "Map of attributes for the session cookie."} CookieAttrsOption]]))

(def csp-keys [:base-uri
               :default-src
               :script-src
               :object-src
               :style-src
               :img-src
               :media-src
               :frame-src
               :child-src
               :frame-ancestors
               :font-src
               :connect-src
               :manifest-src
               :form-action
               :sandbox
               :script-nonce
               :plugin-types
               :reflected-xss
               :block-all-mixed-content
               :upgrade-insecure-requests
               :referrer
               :report-uri
               :report-to])

(def ContentSecurityPolicyData
  (m/schema
   (into [:map {:name ::content-security-policy-data}]
         (mapv (fn [k]
                 [k {:optional true} [:vector :string]])
               csp-keys))))

(def SecHeadersMiddlewareOptions
  (m/schema
   [:map {:name ::sec-headers-middleware-options}
    [:hsts? {:default true :doc "The HTTP Strict-Transport-Security (HSTS) response header makes sure the browser automatically upgrades to HTTPS for current and future connections."} :boolean]
    [:content-security-policy {:default {:font-src        ["'self'"]
                                         :script-src      ["'self'" "'unsafe-eval'"]
                                         :style-src-elem  ["'self'" "'unsafe-inline'"]
                                         :form-action     ["'self'"]
                                         :style-src       ["'self'" "'unsafe-inline'"]
                                         :connect-src     ["'self'"]
                                         :img-src         ["'self'" "https: data:"]
                                         :frame-ancestors ["'none'"]
                                         :base-uri        ["'self'"]
                                         :default-src     ["'none'"]
                                         :media-src       ["'self'" "https: data:"]}}
     ContentSecurityPolicyData]
    [:content-security-policy-raw {:optional true
                                   :doc      "Raw CSP header value. This will override the content-security-policy key if both are present."} :string]
    [:enable-csp-nonce {:optional true
                        :default  #{}
                        :doc      "A set of CSP keys to add a nonce to, defaults to empty. Example #{:script-src}"}
     [:set (into [:enum] csp-keys)]]
    [:x-permitted-cross-domain-policies {:default "none"} :string]
    [:referrer-policy {:default "no-referrer"} :string]
    [:x-content-type-options {:default "nosniff"} :string]
    [:x-frame-options {:optional true :default "deny"} :string]]))
