;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2


;; portal-helpers - a collection of portal render/submit helpers for repl driven development
;; Copyright © 2025 Casey Link
;; SPDX-License-Identifier: EUPL-1.2
(ns ^:clj-reload/no-reload portal-helpers
  (:require
   [portal.api :as p]
   [lambdaisland.ansi :as ansi]
   [clojure.string :as str]))

;; --------------------------------------------------------------------------------------------
;;; Portal Setup

(defn ansi-message->hiccup
  "Convert a string with ANSI escape codes to hiccup so it renders nicely in portal"
  [msg]
  (with-meta
    (->>
     (str/split-lines msg)
     (map (fn [line] [:span (ansi/text->hiccup line) [:br]]))
     (into [:div {:style {:white-space "pre-wrap" :display "flex" :flex-direction "column"}}]))
    {:portal.viewer/default :portal.viewer/hiccup}))

(let [ansi-color-regex #"\033\[[0-9;]*m"]
  (defn strip-colors [s]
    (clojure.string/replace s ansi-color-regex "")))

(defn ex-message-with-key
  "Returns the message string of an exception that is v, or wrapped in v, where the ex-data contains key"
  [v key]
  (cond
    (some? (-> v :result ex-data key))
    (-> v :result ex-message)

    (some? (-> v ex-cause ex-data key))
    (-> v ex-cause ex-message)

    (some? (-> v :result :data  key))
    (-> v :result :cause)))

(defn transform-bling [v]
  (if (and (vector? v) (-> v meta :ansi/color))
    (try
      (ansi-message->hiccup (first v))
      (catch Exception _
        (with-meta
          [:portal.viewer/text (strip-colors (first v))]
          {:portal.viewer/default :portal.viewer/hiccup})))
    v))

(defn transform-reitit-pretty-exceptions
  "Transforms a reitit pretty exception to a hiccup representation so it renders nicely in portal"
  [v]
  (if-let [message (ex-message-with-key v :reitit.exception/cause)]
    (ansi-message->hiccup message)
    v))

(defn strip-meta
  "Given a value v, will strip all metadata matching keys in ks. Returns v with new metadata"
  [v ks]
  (try
    (let [m (meta v)]
      (if m
        (with-meta v (reduce dissoc m ks))
        v))
    (catch Exception _
      v)))

(defn transform-guardrail-explanations
  "Transforms guardrail explanations to text so they render nicely in portal"
  [v]
  (if (:com.fulcrologic.guardrails/explain-human v)
    (update v :com.fulcrologic.guardrails/explain-human (fn [v]
                                                          (with-meta
                                                            [:portal.viewer/text v]
                                                            {:portal.viewer/default :portal.viewer/hiccup})))
    v))

(def recommended-transforms [#'transform-guardrail-explanations
                             #'transform-reitit-pretty-exceptions
                             #'transform-bling
                             #(strip-meta % [:dev.repl/logging])])

(defn transform-taps [transforms v]
  (reduce (fn [v f]
            (f v)) v transforms))

(defn- unwrap-var [v default]
  (if-let [unwrapped (if (var? v)
                       (var-get v)
                       v)]
    unwrapped
    default))

(defn submit*
  "The reloadable extendable implementation of submit.

   It takes the following options:

     - `transforms` - a sequence of single-arity functions that will be applied to the value before it is submitted
     - `tap-routing`- a two-arity function that takes the original value and the transformed value, it should return the tap list
                      by default a single tap list is used for all values."
  [& {:keys [transforms tap-routing]}]
  (fn -submit [v]
    (let [v'     (transform-taps (unwrap-var transforms []) v)
          router (unwrap-var tap-routing nil)
          tap    (when router (router v v'))]
      (if tap
        (swap! tap v v')
        (p/submit v')))))

(defn make-submit [& {:keys [transforms tap-routing]}]
  (when transforms
    (assert (var? transforms) "make-submit: transforms must be a var for reloading to work"))
  (when tap-routing
    (assert (var? tap-routing) "make-submit: tap-routing must be a var for reloading to work"))
  (submit* :transforms transforms :tap-routing tap-routing))

;; --------------------------------------------------------------------------------------------
;;; Tufte + Portal
(def columns
  (-> [:min :p25 :p50 :p75 :p90 :p95 :p99 :max :mean :mad :sum]
      (zipmap (repeat :portal.viewer/duration-ns))
      (assoc :loc :portal.viewer/source-location)))

(defn format-data [stats]
  (-> stats
      (update-in [:loc :ns] symbol)
      (vary-meta update :portal.viewer/for merge columns)))

(defn format-pstats [pstats]
  (-> @pstats
      (:stats)
      (update-vals format-data)
      (with-meta
        {:portal.viewer/default :portal.viewer/table
         :portal.viewer/table
         {:columns [:n :min #_:p25 #_:p50 #_:p75 #_:p90 #_:p95 #_:p99 :max :mean #_:mad :sum :loc]}})))

(defn add-tufte-tap-handler!
  "Adds tufte handler that sends `profile` stats to `tap>`

  Requires that taoensso/tufte is on the classpath."
  [{:keys [ns-pattern handler-id]
    :or   {ns-pattern "*"
           handler-id :basic-tap}}]
  ((requiring-resolve 'taoensso.tufte/add-handler!)
   handler-id ns-pattern
   (fn [{:keys [?id ?data pstats]}]
     (tap> (vary-meta
            (format-pstats pstats)
            merge
            (cond-> {}
              ?id   (assoc :id ?id)
              ?data (assoc :data ?data)))))))

;; --------------------------------------------------------------------------------------------
;;; Telemere + Portal
(defn telemere->tap
  ([{:keys [id msg_ level inst] :as signal}]
   (try
     (tap>
      (with-meta
        (->
         ;; remove a bunch of nil values
         (into {} (remove #(nil? (val %))) signal)
         ;; add result which the portal viewer will use to display the log message
         (assoc :result [level (or id (force msg_))])
         ;; viewer doesn't support java.time.Instant
         (assoc :time (java.util.Date/from inst))
         (dissoc :inst)
         ;; telemere provides ns a a string, but viewer wants a symbol
         (update :ns  symbol)
         ;; add in runtime to get a nice logo (doesn't work?)
         (assoc :runtime ((requiring-resolve 'portal.console/runtime))))
        {:portal.viewer/default :portal.viewer/log
         :dev.repl/logging      true
         :portal.viewer/for
         {:form :portal.viewer/pprint
          :time :portal.viewer/relative-time}}))
     (catch Exception _)))
  ([]
   #_(tap>
      (with-meta
        [:signal "telemere handler has shut down"]
        {:dev.repl/logging true}))))

(defn add-telemere-tap-handler!
  "Adds a handler to telemere that will `tap>` logs/events

  Requires that taoensso/telemere is on the classpath."
  []
  ((requiring-resolve 'taoensso.telemere/remove-handler!) :tap-handler)
  ((requiring-resolve 'taoensso.telemere/add-handler!) :tap-handler telemere->tap
                                                       {:min-level :debug}))

(comment
  #_(do
      (add-telemere-tap-handler!)
      (taoensso.telemere/log! {:level :error :data {:err "Exception"}} "Error oh noes!")
      (taoensso.telemere/log! {:level :info :data {:interesting :fact}} "Did you know that..")
      (taoensso.telemere/log! {:level :debug :data {:data [42]}} "datadatatatadatatatata")))
