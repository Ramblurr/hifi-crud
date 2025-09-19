;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.system.middleware.csrf
  "A simple HMAC double-submit cookie implementation"
  (:require
   [hifi.config :as config]
   [clojure.string :as str]
   [hifi.util.crypto :as crypto]
   [hifi.system.middleware.spec :as options]))

(defn ->csrf-middleware-opts [v]
  (options/valid! "csrf-middleware"
                  options/CSRFProtectionMiddlewareOptions
                  (options/coerce options/CSRFProtectionMiddlewareOptions (or v {}))))

(defn message [sid rand-val]
  (str
   (count sid) "!" sid
   8 "!"
   rand-val))

(defn generate-csrf-token [csrf-keyspec sid]
  (let [rand-val (crypto/rand-string 8)
        message  (message sid rand-val)
        hmac     (crypto/hmac-sha256 csrf-keyspec message)]
    (str hmac "." rand-val)))

(defn valid-csrf-token? [csrf-keyspec csrf-token sid]
  (when csrf-token
    (let [[hmac rand-val] (str/split csrf-token #"\.")
          message         (message sid rand-val)
          expected-hmac   (crypto/hmac-sha256 csrf-keyspec message)]
      (crypto/eq? hmac expected-hmac))))

(comment
  (let [secret  "hunter2"
        keyspec (crypto/secret-key->hmac-sha256-keyspec (config/unmask secret))
        token   (generate-csrf-token keyspec "12345")]
    (valid-csrf-token? keyspec token "12345")))

(defn csrf-cookie-value [csrf-token {:keys [cookie-attrs]}]
  (merge (dissoc cookie-attrs :secure-prefix :host-prefix)
         {:value csrf-token}))

(defn csrf-cookie-name [{:keys [cookie-name cookie-attrs] :as _opts}]
  (cond
    (:secure-prefix cookie-attrs)
    (str "__Secure-" cookie-name)
    (:host-prefix cookie-attrs)
    (str "__Host-" cookie-name)
    :else cookie-name))

(defn update-csrf-cookie [response csrf-token options]
  (assoc-in response [:cookies (csrf-cookie-name options)] (csrf-cookie-value csrf-token options)))

(defn csrf-middleware
  ([options]
   (let [options      (->csrf-middleware-opts options)
         csrf-keyspec (crypto/secret-key->hmac-sha256-keyspec (-> options :csrf-secret config/unmask!))
         validate     (partial valid-csrf-token? csrf-keyspec)
         generate     (partial generate-csrf-token csrf-keyspec)]
     {:name           ::csrf
      :options-schema options/CSRFProtectionMiddlewareOptions
      :wrap           (fn csrf-middleware-wrap [handler]
                        (fn csrf-middleware
                          ([request]
                           (let [submitted-csrf-token (:hifi/submitted-csrf-token request) ;; whichever middleware parses the body is responsible for adding this
                                 sid                  (:sid request)
                                 valid-token?         (when submitted-csrf-token (validate submitted-csrf-token sid))]
                             (cond
                               (and sid valid-token?)
                               (handler (-> request (assoc :hifi/csrf-token submitted-csrf-token)
                                            (dissoc :hifi/submitted-csrf-token)))

                               (and sid (= (:request-method request) :get))
                               (let [new-csrf-token (generate sid)
                                     response       (handler (assoc request :hifi/csrf-token new-csrf-token))]
                                 (update-csrf-cookie response new-csrf-token options))

                               :else {:status  403
                                      :headers {"Content-Type" "text/plain"}
                                      :body    "Invalid csrf"})))))})))

(def CSRFProtectionMiddlewareComponentData
  {:name           :csrf-protection
   :factory        #(csrf-middleware %)
   :options-schema options/CSRFProtectionMiddlewareOptions})

(comment

  (require '[reitit.middleware :as middleware])

  (let [mw      (csrf-middleware
                 (->csrf-middleware-opts nil))
        handler (fn [request]
                  {:status 200
                   :body   (str "Hello sid " (:sid request))})]

    (= {:status 403} ((middleware/chain [mw] handler) {}))
    ((middleware/chain [mw] handler) {:request-method :get})
    ((middleware/chain [mw] handler) {:request-method :get :headers {"cookie" "__Host-sid=DvFBTJEsLTjS1pjsWK5maJJG47s;"}}))

  ;;
  )
