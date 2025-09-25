(ns hifi.util.timer
  "Dependency-free debouncing & throttling utilities. "
  (:refer-clojure :exclude [flush])
  (:import [java.util Timer TimerTask]))

(defonce ^Timer ^:private default-timer (Timer. true))

(defn- now-ms ^long [] (System/currentTimeMillis))

(defn- schedule!
  "Schedules (fn) to run after `delay-ms` on `timer`. Returns the TimerTask."
  ^TimerTask [^Timer timer delay-ms f]
  (let [task (proxy [TimerTask] [] (run [] (f)))]
    (.schedule timer task (long (max 0 delay-ms)))
    task))

(defn- cancel-task! [^TimerTask task]
  (when task
    (.cancel task)
    true))

(defn- with-controls
  "Attach control fns via metadata so we can call flush!/cancel!/pending?."
  [wrapped {:keys [flush! cancel! pending?]}]
  (with-meta wrapped
    {::flush!   flush!
     ::cancel!  cancel!
     ::pending? pending?}))

(defn flush!
  "Immediately invoke a pending trailing call (if any) for `wrapped`.
   Safe to call even if nothing is pending."
  [wrapped]
  (when-let [f (-> wrapped meta ::flush!)]
    (f)))

(defn cancel!
  "Cancel any scheduled executions for `wrapped` and clear pending state."
  [wrapped]
  (when-let [f (-> wrapped meta ::cancel!)]
    (f)))

(defn pending?
  "Return true if `wrapped` has a pending trailing invocation."
  [wrapped]
  (if-let [f (-> wrapped meta ::pending?)]
    (boolean (f))
    false))

(defn debounce
  "Return a debounced wrapper around `f`.

  Options map `opts` (all optional):
  - :wait        <int ms>   Debounce window. Default 200.
  - :leading?    <bool>     Fire on the leading edge. Default false.
  - :trailing?   <bool>     Fire on the trailing edge. Default true.
  - :max-wait    <int ms>   Guarantee an invoke at most this often.
  - :timer       <Timer>    Custom java.util.Timer to schedule on.
  - :on-error    <fn ex>    Error handler (default rethrow).

  The returned value is a function you can call like the original `f`.
  You can also use control helpers:
    (util.debounce/flush! wrapped)
    (util.debounce/cancel! wrapped)
    (util.debounce/pending? wrapped)"
  [f & [{:keys [wait leading? trailing? max-wait timer on-error]
         :or   {wait     200 leading? false trailing? true timer default-timer
                on-error (fn [^Throwable ex] (throw ex))}}]]
  (let [state   (atom {:last-call-ms           nil  ;; last time wrapper was *called*
                       :last-invoke-ms         nil ;; last time f actually ran
                       :trailing-task          nil
                       :maxwait-task           nil
                       :pending-args           nil
                       :has-pending?           false
                       :first-call-in-burst-ms nil})
        ;; run f with latest args, handling errors
        invoke! (fn []
                  (let [args (:pending-args @state)]
                    (swap! state assoc
                           :last-invoke-ms (now-ms)
                           :pending-args nil
                           :has-pending? false
                           :first-call-in-burst-ms nil)
                    (try
                      (apply f args)
                      (catch Throwable ex
                        (on-error ex)))))

        ;; schedule trailing run after wait from 'now'
        schedule-trailing! (fn []
                             (when-let [t (:trailing-task @state)]
                               (cancel-task! t))
                             (let [task (schedule! timer wait invoke!)]
                               (swap! state assoc :trailing-task task)))

        ;; schedule max-wait enforcement from first call in burst
        ensure-maxwait! (fn []
                          (when (and max-wait (pos? (long max-wait)))
                            (let [{:keys [first-call-in-burst-ms maxwait-task]} @state]
                              (when (nil? first-call-in-burst-ms)
                                (swap! state assoc :first-call-in-burst-ms (now-ms)))
                              (when maxwait-task
                                ;; if one already exists, keep it; we only set on burst start
                                nil)
                              (when (nil? (:maxwait-task @state))
                                (let [delay-ms (max 0 (- (+ (:first-call-in-burst-ms @state) max-wait)
                                                         (now-ms)))
                                      task     (schedule! timer delay-ms
                                                          (fn []
                                                            (when (:has-pending? @state)
                                                              (invoke!))))]
                                  (swap! state assoc :maxwait-task task))))))

        ;; cancel scheduled tasks
        cancel-all! (fn []
                      (let [{:keys [trailing-task maxwait-task]} (swap! state identity)]
                        (when trailing-task (cancel-task! trailing-task))
                        (when maxwait-task  (cancel-task! maxwait-task)))
                      (swap! state assoc
                             :trailing-task nil
                             :maxwait-task  nil
                             :has-pending?  false
                             :pending-args  nil
                             :first-call-in-burst-ms nil))

        ;; public controls
        flush-fn    (fn []
                      (let [{:keys [has-pending?]} @state]
                        (when has-pending?
                          (when-let [t (:trailing-task @state)] (cancel-task! t))
                          (when-let [m (:maxwait-task  @state)] (cancel-task! m))
                          (swap! state assoc :trailing-task nil :maxwait-task nil)
                          (invoke!))))
        cancel-fn   (fn [] (cancel-all!))
        pending?-fn (fn [] (:has-pending? @state))

        ;; the wrapped IFn
        wrapped (fn [& args]
                  (let [n                        (now-ms)
                        {:keys [last-invoke-ms]} @state
                        time-since-invoke        (when last-invoke-ms (- n last-invoke-ms))
                        can-lead?                (and leading?
                                                      (or (nil? time-since-invoke)
                                                          (>= ^long time-since-invoke ^long wait)))]
                    ;; update shared state
                    (swap! state assoc
                           :last-call-ms n
                           :pending-args args
                           :has-pending? true)

                    (cond
                      ;; Leading edge execution
                      can-lead?
                      (do
                        ;; run immediately
                        (swap! state assoc :has-pending? false :pending-args nil)
                        (try
                          (apply f args)
                          (catch Throwable ex
                            (on-error ex)))
                        (swap! state assoc :last-invoke-ms (now-ms))
                        ;; Optionally schedule trailing if enabled (captures extra calls within window)
                        (when trailing?
                          (schedule-trailing!))
                        (ensure-maxwait!)
                        nil)

                      ;; Otherwise, just (re)schedule trailing and possibly max-wait
                      trailing?
                      (do
                        (schedule-trailing!)
                        (ensure-maxwait!)
                        nil)

                      ;; Neither leading nor trailing allowed: just set max-wait to ensure progress
                      :else
                      (ensure-maxwait!))))]

    (with-controls wrapped
      {:flush!   flush-fn
       :cancel!  cancel-fn
       :pending? pending?-fn})))

(defn throttle
  "Return a throttled wrapper around `f`.

  Options map `opts` (all optional):
  - :interval   <int ms>   Minimum time between invocations. Default 200.
  - :leading?   <bool>     Fire on the leading edge. Default true.
  - :trailing?  <bool>     Fire on the trailing edge for a final call in the window. Default true.
  - :timer      <Timer>    Custom timer.
  - :on-error   <fn ex>    Error handler (default rethrow).

  Implementation note: built on `debounce` with leading+trailing semantics
  and :wait = :interval. This matches lodash-style throttle."
  [f & [{:keys [interval leading? trailing? timer on-error]
         :or   {interval 200 leading? true trailing? true
                timer default-timer
                on-error (fn [^Throwable ex] (throw ex))}}]]
  (apply debounce f
         [{:wait      interval
           :leading?  leading?
           :trailing? trailing?
           :timer     timer
           :on-error  on-error}]))

(comment

  (def on-save* (debounce
                 (fn [] (tap> "Saving... "))
                 {:wait 1000 :leading? false :trailing? true :max-wait 2000}))

  (on-save*)
  (pending? on-save*) ;; => true/false
  (flush! on-save*)
  (cancel! on-save*)
  ;;
  )
