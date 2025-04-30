;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.system.middleware.exception
  "Middleware for gracefully handling exceptions both during development and in production."
  (:require [malli.core :as m]
            [hifi.system.middleware.spec :as options]
            [malli.experimental.lite :as l]
            [hifi.error.iface :as pe]
            [reitit.ring :as ring]))

;; --------------------------------------------------------------------------------------------
;;; Pattern matching impl

(defn ->pattern [k]
  (cond
    (keyword? k) k
    (class? k)   [:fn #(instance? k %)]
    (map? k)     (l/schema k)
    (vector? k)  k
    :else        (throw (ex-info "Invalid pattern" {:pattern k}))))

(defn ->patterns [m]
  (into []
        (concat [:altn]
                (map (fn [[k v]] [v (->pattern k)]) m)
                [[:default :any]])))

(defn prepare-handlers [handlers]
  (let [default  (::default handlers)
        wrap     (::wrap handlers)
        patterns (->patterns (dissoc handlers ::default ::wrap))]
    {:patterns patterns
     :default  default
     :wrap     wrap}))

(defn parse [patterns e]
  (first
   (m/parse patterns [e])))

(defn match-handler [{:keys [patterns default]} e]
  (let [handler (parse patterns (or (ex-data e) e))]
    (if (= :default handler)
      default
      handler)))

;; --------------------------------------------------------------------------------------------
;;; Invoking handlers

(defn call-handler [matching-opts handler error request]
  (if-let [wrapping-handler (:wrap matching-opts)]
    (wrapping-handler handler error request)
    (handler error request)))

(defn handle-exceptions-int [matching-opts {:keys [error request] :as ctx}]
  (let [handler      (match-handler matching-opts error)
        new-response (call-handler matching-opts handler error request)]
    (if (instance? Exception new-response)
      (-> ctx (assoc :error new-response) (dissoc :response))
      (-> ctx (assoc :response new-response) (dissoc :error)))))

(defn handle-exceptions [matching-opts error request]
  (let [handler      (match-handler matching-opts error)
        new-response (call-handler matching-opts handler error request)]
    (if (instance? Exception new-response)
      (throw new-response)
      new-response)))

;; --------------------------------------------------------------------------------------------
;;; Default Handlers
;; An absolutely minimal set of default handlers
;; The goal is that by default there should be no information leakage to the client
;; Developers definitely should provide their own handlers

(defn default-exception-handler
  [^Exception _e _]
  {:status  500
   :headers {"Content-Type" "text/plain"}
   :body    "Internal server error"})

(defn default-not-found-handler
  [^Exception _e _]
  {:status 404
   :body   "404 Page not found"})

(defn create-status-handler [status msg]
  (fn [_ _]
    {:status status
     :body   msg}))

(defn http-response-handler
  "Reads response from Exception ex-data :response"
  [e _]
  (-> e ex-data :response))

(defn request-parsing-handler [e _]
  {:status  400
   :headers {"Content-Type" "text/plain"}
   :body    (str "Malformed " (-> e ex-data :format pr-str) " request")})

(def default-exception-handlers
  {::default                                       default-exception-handler
   ::wrap                                          (fn [handler e req]
                                                     (handler e req))
   {:type [:= ::ring/response]}                    http-response-handler
   {:type [:= :muuntaja/decode]}                   request-parsing-handler
   {:type [:= :reitit.coercion/request-coercion]}  (create-status-handler 400 "Request coercion failed")
   {:type [:= :reitit.coercion/response-coercion]} (create-status-handler 500 "Response coercion failed")})

;; --------------------------------------------------------------------------------------------
;;; Middleware

(defn debug-error! [_request e]
  (tap> e))

(defn- on-exception [handlers debug-errors? error request respond raise]
  (try
    (tap> [:on-exception debug-errors? error])
    (when debug-errors?
      (debug-error! request error))
    (respond (handle-exceptions handlers error request))
    (catch Exception e
      (raise e))))

(defn- wrap [debug-errors? prepared-handlers]
  (fn [handler]
    (fn
      ([request]
       (try
         (handler request)
         (catch Throwable e
           (on-exception prepared-handlers debug-errors? e request identity #(throw %)))))
      ([request respond raise]
       (try
         (handler request respond (fn [e] (on-exception prepared-handlers debug-errors? e request respond raise)))
         (catch Throwable e
           (on-exception prepared-handlers debug-errors? e request respond raise)))))))

(defn exception-middleware
  ([] (exception-middleware {}))
  ([opts]
   (let [{:keys [debug-errors? error-handlers]} (pe/coerce! options/ExceptionMiddlewareOptions (or opts {}))
         prepared-handlers                      (prepare-handlers (or error-handlers default-exception-handlers))]
     {:name           ::exception-middleware
      :options-schema options/ExceptionMiddlewareOptions
      :wrap           (wrap debug-errors? prepared-handlers)})))

(defn- report-backstop-and-return [report request e]
  (when report
    (try
      (report e request)
      (catch Throwable _)))
  {:status  500
   :body    "Internal Server Error"
   :headers {"Content-Type" "text/plain"}})

(defn- wrap-backstop [report]
  (fn [handler]
    (fn wrap-backstop-req
      ([request]
       (try
         (handler request)
         (catch Throwable e
           (report-backstop-and-return report request e))))
      ([request respond _raise]
       (try
         (handler request respond (fn [e]
                                    (report-backstop-and-return report request e)))
         (catch Throwable e
           (report-backstop-and-return report request e)))))))

(defn exception-backstop-middleware
  "Creates middleware that serves as the final safety mechanism to prevent
   exceptions from propagating to the servlet/http container.

   When placed as the outermost middleware in a chain, it ensures that any
   unhandled exceptions are converted to proper HTTP responses.

   Options:
     :report - A side-effecting function that takes [exception request] for logging/reporting.
               Defaults to a function that uses tap> to report the error. The return value is discarded."
  ([] (exception-backstop-middleware {}))
  ([{:keys [report]
     :or   {report (fn [e req]
                     (prn e)
                     (tap> [:exception-backstop :error e :request req]))}}]
   {:name           ::exception-backstop-middleware
    :options-schema options/ExceptionBackstopMiddlewareOptions
    :wrap           (wrap-backstop report)}))

(comment
  ;; Thought: Pedestal's core.match macro based exception handling is nice and expressive,
  ;;          but it it uses a macro which prevents manipulating error handlers as data.
  ;;          Contrast that to reitit.http.interceptors.exception/exception-interceptor which treats handlers as data
  ;;          but has much less expressive matching.
  ;;          Why can't we have the best of both?
  ;;
  ;; Idea: Use malli.core/parse as an alternative to core.match!

  ;; Example usage
  (def handlers
    "handlers is a map of pattern -> error handler fn
  The keys can be:
    - class  - matches based on the class of the exception
    - map    - a malli lite schema
    - vector - a malli schema

  There are two special keys:
    - :hifi.interceptors.errors/default - a default handler, if none of the patterns match
    - :hifi.interceptors.errors/wrap    - a (fn [handler e req]) which will wrap the actual handler, useful for handling cross-cutting concerns
  "
    {;; match based on exception class  type
     java.lang.ArithmeticException              (constantly :math-error)
     ;; match based on the value of the :type key in the ex-data
     {:type [:= :error]}                        (constantly :error)
     ;; match based on the value of the :category key in the ex-data
     {:category [:= :horror]}                   (constantly :horror)
     ;; match based on the presence of a :anomaly key, regardless of its value
     {:anomaly :any}                            (constantly :anomaly)
     ;; while the malli lite format is more concise, perhaps you want to use the normal malli format
     [:map [:a-number [:and :int [:fn even?]]]] (constantly :even)
     ;; a default handler
     ::default                                  (constantly :default)
     ;; a wrapping handler for cross cutting concerns
     ::wrap                                     (fn [handler e req]
                                                  (let [result (handler e req)]
                                                    (tap> [:from-wrap result])
                                                    result))})

  (defn handle-error [opts e req]
    (let [matching-opts (prepare-handlers opts)
          handler       (match-handler matching-opts e)]
      (if-let [wrapping-handler (:wrap matching-opts)]
        (wrapping-handler handler e req)
        (handler e req))))
  (handle-error handlers 1 {})
  (handle-error handlers {:different 1} {})
  (handle-error handlers (ex-info "message" {:type :error}) {})
  (handle-error handlers (ex-info "message" {:category :horror}) {})
  (handle-error handlers (ex-info "message" {:anomaly :anything}) {})
  (handle-error handlers (ex-info "message" {:anomaly ["really" "anything"]}) {})
  (handle-error handlers (ex-info "message" {:a-number 2}) {})
  (handle-error handlers (ex-info "message" {:a-number 3}) {})
  (handle-error handlers (ex-info "message" {}) {})
  (handle-error handlers (java.lang.ArithmeticException. "message") {})

  ;; this matches on the key in the map
  (m/parse [:altn
            [:has-type-key [:map [:type :keyword]]]
            [:invalid :any]]
           [{:type :error}])
  ;; => [:has-type-key {:type :error}]

  ;; but we want to match on the value of :type
  (m/parse [:altn
            [:potates [:map [:type [:= :potates]]]]
            [:beans [:map [:type [:= :beans]]]]
            [:invalid :any]]
           [{:type :beans :other :stuff}])

  (m/parse [:catn [:a [:= 1]] [:b :any] [:c [:= 3]] [:rest [:* :any]]] '[1 2 3 4 5 6])
  ;; => {:a 1, :b 2, :c 3, :rest [4 5 6]}
  )
