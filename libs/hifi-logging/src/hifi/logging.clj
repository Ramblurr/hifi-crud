;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.logging
  "Standarized logging and telemetry for HIFI applications."
  (:require
   [charred.api :as charred]
   [donut.system :as ds]
   [hifi.core :as h]
   [hifi.logging.spec :as spec]
   [hifi.util.shutdown :as shutdown]
   [taoensso.telemere :as t]))

;; --------------------------------------------------------------------------------------------
;;; Some sane defaults

(t/set-min-level! nil #{"datomic.peer" "datomic.process-monitor"
                        "datomic.log" "datomic.kv-cluster"
                        "datomic.reconnector2" "datomic.common"
                        "datomic.db" "datomic.slf4j"} :warn)

;; --------------------------------------------------------------------------------------------
;;; Use Telemere as our clojure tools.logging backend, but only if the deps are present
(try
  ((requiring-resolve 'taoensso.telemere.tools-logging/tools-logging->telemere!))
  (catch Throwable _))

;; -------------------------------------------------------------------------------------------- a
;;; Telemere is our slf4j backend -> no code here.
;;; Because com.taoensso/telemere-slf4j exists on the classpath (see deps.edn)
;;; it is automatically picked up.

;; --------------------------------------------------------------------------------------------
;;; tap> handler (w/ Portal support)
(defn telemere-tap-handler
  "A telemere handler that tap>s signals"
  ([{:keys [id msg_ level inst coords] :as signal}]
   (try
     (tap>
      (with-meta
        (->
         ;; remove a bunch of nil values
         (into {} (remove #(nil? (val %))) signal)
         ;; The following tranformations are done to make the signal into a shape
         ;; that the portal.ui.viewer.log (cljs) ns will accept as a "log", to apply its formatting/view
         ;; add result which the portal viewer will use to display the log message
         (assoc :result [level (or id (force msg_))])
         ;; viewer doesn't support java.time.Instant
         (assoc :time (java.util.Date/from inst))
         (dissoc :inst)
         ;; telemere provides ns a a string, but viewer wants a symbol
         (update :ns  symbol)
         (assoc :msg (force msg_))
         (assoc :line (or (first coords) -1))
         (assoc :column (or (second coords) -1))
         (dissoc :msg_)
         ;; add in runtime to get a nice logo
         (assoc :runtime :clj))
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
  (t/add-handler! ::tap-handler telemere-tap-handler
                  {:min-level :debug}))

(h/defcomponent TelemereTapHandlerComponent
  "TODO docstring"
  {::ds/start        (fn [{config :donut.system/config}]
                       (shutdown/add-shutdown-hook! ::telemere-shutdown t/stop-handlers!)
                       (when (-> config :hifi/options :enabled?)
                         (add-telemere-tap-handler!)))
   ::ds/stop         (fn  [_]
                       (t/remove-handler! ::tap-handler))
   :hifi/wanted-by   :hifi.target/early
   :hifi/config-key  :hifi.logging/telemere-tap
   :hifi/config-spec spec/TelemereTapHandlerComponentOptions})

;; --------------------------------------------------------------------------------------------
;;; Console Logging

;; Add console handler to print signals as edn

(def default-write-json (charred/write-json-fn {}))

(h/defcomponent ConsoleLoggingComponent
  "TODO docstring"
  {::ds/start        (fn start-console-logging [{config :donut.sysktem/config}]
                       (t/remove-handler! :default/console)
                       (shutdown/add-shutdown-hook! ::telemere-shutdown t/stop-handlers!)
                       (when (-> config :hifi/options :enabled?)
                         (condp = (-> config :hifi/options :format)
                           :json
                           (t/add-handler! ::logging-console
                                           (t/handler:console
                                            {:output-fn default-write-json}))
                           :edn
                           (t/add-handler! ::logging-console
                                           (t/handler:console
                                            {:output-fn (t/pr-signal-fn {:pr-fn :edn})}))
                           :pretty
                           (t/add-handler! ::logging-console
                                           (t/handler:console
                                            {:output-fn (t/format-signal-fn {})}))
                           nil)))
   ::ds/stop         (fn [_]
                       (t/remove-handler! ::logging-console))
   :hifi/wanted-by   :hifi.target/early
   :hifi/config-spec spec/ConsoleLoggingComponentOptions
   :hifi/config-key  :hifi.logging/console})

(h/defplugin plugin
  "TODO docstring"
  {:hifi/logging {:hifi.logging/console      ConsoleLoggingComponent
                  :hifi.logging/telemere-tap TelemereTapHandlerComponent}})
