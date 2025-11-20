;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.web.middleware.session
  (:require
   [hifi.util.crypto :as crypto]
   [hifi.web.middleware.spec :as options]
   [ring.middleware.cookies :as cookies]))

(defn ->session-middleware-opts [v]
  (options/valid! "session-middleware"
                  options/SessionMiddlewareOptions
                  (options/coerce options/SessionMiddlewareOptions (or v {}))))

(defn session-cookie-value [sid session-cookie-attrs {:keys [cookie-attrs]}]
  (merge (dissoc cookie-attrs :secure-prefix :host-prefix)
         session-cookie-attrs
         {:value sid}))

(defn session-cookie-name [{:keys [cookie-name cookie-attrs]}]
  (cond
    (:secure-prefix cookie-attrs)
    (str "__Secure-" cookie-name)
    (:host-prefix cookie-attrs)
    (str "__Host-" cookie-name)
    :else cookie-name))

(session-cookie-value "1234" nil (->session-middleware-opts nil))
(session-cookie-name  (->session-middleware-opts nil))

(defn update-session-cookie [response sid options]
  (-> response
      (assoc-in [:cookies (session-cookie-name options)] (session-cookie-value sid (:session-cookie-attrs response) options))
      (dissoc :sid :session-cookie-attrs)
      (cookies/cookies-response)))

(defn get-sid [request options]
  (get-in request [:cookies (session-cookie-name options) :value]))

(defn session-middleware
  "Creates a Cross-Site Request Forgery (CSRF) protection middleware using HMAC double-submit cookie pattern.

  Takes a map of options that conform to [[hifi.web.middleware.spec/CSRFProtectionMiddlewareOptions]] schema:

  - `:csrf-secret` (required): A secure random secret used for signing CSRF tokens (suggested: 32 random bytes encoded as string)
  - `:cookie-name` (default: \"csrf\"): Base name for the CSRF cookie
  - `:cookie-attrs` (default: `{:same-site :lax :secure true :path \"/\" :host-prefix true}`): Cookie attributes

  Returns a reitit middleware map.

  ### Protection Mechanism

  This middleware implements CSRF protection:
  1. For GET requests: generating a secure token stored in both the request map and a cookie
  2. For non-GET requests: validating the submitted token against the expected value
  3. Rejecting requests with invalid CSRF tokens with a 403 response

  ### Requirements

  - **Session middleware** must be configured to run before this middleware (from [[hifi.web.middleware.session]])
  - The request must have a valid `:sid` (session ID) for token generation
  - **Body-parsing middleware** must extract the token from request body and add it as `:hifi/submitted-csrf-token`
  - The client must extract and submit the CSRF token with all non-GET requests"
  ([] (session-middleware {}))
  ([options]
   (let [options (->session-middleware-opts options)]
     {:name           ::session
      :config-spec options/SessionMiddlewareOptions
      :wrap           (fn session-middleware-wrap [handler]
                        (fn session-middleware
                          ([request]
                           (let [request (cookies/cookies-request request options)
                                 sid     (get-sid request options)]
                             (cond
                               sid
                               (let [response      (handler (assoc request :sid sid))
                                     session-attrs (:session-cookie-attrs response)
                                     new-sid       (:sid response)]
                                 (if (or (and new-sid (not= sid new-sid))
                                         (and session-attrs (or sid new-sid)))
                                   (update-session-cookie response (or new-sid sid) options)
                                   response))

                               (= (:request-method request) :get)
                               (let [new-sid  (crypto/new-uid)
                                     response (handler (assoc request :sid new-sid))]
                                 (update-session-cookie response new-sid options))
                               :else {:status 403})))))})))

(def SessionMiddlewareComponentData
  {:name           :session-cookie
   :factory        #(session-middleware %)
   :config-spec options/SessionMiddlewareOptions})

(comment

  (require '[reitit.middleware :as middleware])

  (let [mw      (session-middleware
                 (->session-middleware-opts nil))
        handler (fn [request]
                  {:status 200
                   :body   (str "Hello sid " (:sid request))})]

    (= {:status 403} ((middleware/chain [mw] handler) {}))
    ((middleware/chain [mw] handler) {:request-method :get})
    ((middleware/chain [mw] handler) {:request-method :get :headers {"cookie" "__Host-sid=DvFBTJEsLTjS1pjsWK5maJJG47s;"}}))

  ;;
  )
