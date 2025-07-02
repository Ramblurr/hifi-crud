(ns hifi.system.middleware.remote-addr
  (:require [clojure.string :as str]))

(defn wrap-http-kit-set-real-remote-address
  "Middlware to override http-kit's default behavior for remote address resolution.

   http-kit trusts X-Forwarded-For headers by default, which is insecure
   when running without an HTTPS proxy (e.g., on localhost or LAN).

   This middleware extracts the actual client IP from the AsyncChannel's
   string representation.

  This middleware should be used before [[wrap-parse-x-forwarded-for]] if both are used."
  [handler]
  (fn [request]
    (if (:async-channel request)
      (let [[_ real-remote-address] (re-matches #".*<->.*/(.*):\d+$" (str (:async-channel request)))]
        (handler (assoc request :remote-addr real-remote-address)))
      (handler request))))

(comment
  (defn- extract-first-xff-ip
    [request {:keys [:reverse-proxy? :forward-headers-insecure]}]
    (if-let [forwarded-for (get-in request [:headers "x-forwarded-for"])]
      (let [remote-addr (str/trim (re-find #"^[^,]*" forwarded-for))]
        (assoc request :hifi/remote-addr remote-addr))
      (assoc request :hifi/remote-addr (:remote-addr request))))

  (defn wrap-parse-x-forwarded-for
    "Middleware that adds the :hifi/remote-addr key to the request map with the value of the first ip present in
  the X-Forwarded-For header, or :remote-addr if there is no XFF header.

  This middleware should be used after [[wrap-http-kit-set-real-remote-address]] if both are used.

  See: [[wrap-parse-x-forwarded-for]]"
    [handler]
    (fn
      ([request]
       (handler (extract-first-xff-ip request)))
      ([request respond raise]
       (handler (extract-first-xff-ip request) respond raise))))

  (def HttpKitSetRealRemoteAddressComponentData
    {:name           ::http-kit-set-real-remote-address
     :options-schema nil
     :factory        (constantly {:wrap wrap-http-kit-set-real-remote-address})})

  (def ParseXForwardedForComponentData
    {:name           ::parse-x-forwarded-for
     :options-schema nil
     :factory        (constantly {:wrap wrap-parse-x-forwarded-for})}))
