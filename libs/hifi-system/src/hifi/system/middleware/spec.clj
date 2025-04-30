;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.system.middleware.spec
  (:require
   [hifi.env :as env]
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
   [:map
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
     [:map
      [:app-namespaces
       {:optional true
        :doc      "Controls which namespaces show up on the pretty errors page as \"application\" frames. All frames from namespaces prefixed with the names in the list will be marked as application frames."}
       [:sequential {:error/message "should be a sequence of app namespaces symbols"} :symbol]]
      [:skip? {:doc      "Allows for skipping the pretty exceptions middleware for a request. Should be a predicate function that takes the request as its argument."
               :optional true}
       [:fn {:error/message "should be a predicate function"} fn?]]]))

(def ExceptionMiddlewareOptions
  (m/schema
   [:map
    [:debug-errors? {:doc     "When true uses hifi.system.middleware.errors functionality for debugging application failures."
                     :default false} :boolean]
    [:error-handlers {:doc      "TODO"
                      :optional true} [:map-of :any fn?]]
    #_[:pretty-exceptions-opts {:doc     "Options for the pretty exceptiosn page handler"
                                :default {}} PrettyExceptionsPageOptions]]))

(def ExceptionBackstopMiddlewareOptions
  (m/schema
   [:map
    [:report {:optional true
              :doc      "A side-effecting function that takes [exception request] for logging/reporting. Defaults to a function that uses tap> to report the error. The return value is discarded."}
     [:fn fn?]]]))

(def CSRFProtectionMiddlewareOptions
  (m/schema
   [:map
    [:csrf-secret {:doc "A randomly generated random secret used to sign the CSRF token."} [:fn env/secret?]]
    [:cookie-name {:default "csrf" :doc "Name of the cookie holding the csrf double-submit token."} NonBlankString]
    [:cookie-attrs {:default {:same-site :lax :secure true :path "/" :host-prefix true}
                    :doc     "Map of attributes for the csrf cookie."} CookieAttrsOption]]))

(def SessionMiddlewareOptions
  (m/schema
   [:map
    [:cookie-name {:default "sid" :doc "Name of the cookie holding the session key."} NonBlankString]
    [:cookie-attrs {:default {:same-site :lax :http-only true :secure true :path "/" :host-prefix true}
                    :doc     "Map of attributes for the session cookie."} CookieAttrsOption]]))
