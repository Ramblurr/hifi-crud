(ns hifi.core.main
  (:require
   [donut.system :as ds]
   [hifi.core.system :as system]
   [hifi.util.shutdown :as shutdown]
   [taoensso.trove :as trove]))

(defonce running-system_ (atom nil))

(defn load-guardrails-silently []
  (let [gr-enabled? (System/getProperty "guardrails.enabled")
        report-info-var (try (requiring-resolve 'com.fulcrologic.guardrails.utils/report-info)
                             (catch Throwable _))]
    (when (and gr-enabled? report-info-var)
      (alter-var-root report-info-var (fn [_] (fn [_]))))
    (boolean (and gr-enabled? report-info-var))))

(defn require-config-loader
  "A config-loader is an arity-1 function that accepts an opts argument and returns a configuration map"
  [config-loader]
  (if config-loader
    (try
      (requiring-resolve config-loader)
      (catch java.io.FileNotFoundException _
        (trove/log! {:msg (str "config-loader not found: " config-loader)})
        nil))
    (try
      (when-let [read-config (requiring-resolve 'hifi.config/read-config)]
        (fn [opts]
          (read-config "config/hifi.edn" opts)))
      (catch java.io.FileNotFoundException _
        (throw (ex-info "No config-loader provided. Add hifi.config to your class path or provide a config-loader" {}))))))

(defn stop
  "Stop the application"
  []
  (when @running-system_
    (reset! running-system_ (ds/stop @running-system_))))

#_(require '[hifi.config])
#_(defn print-system [sys]
    (let [cleaned (walk/postwalk
                   (fn [x]
                     (if (and (map? x) (:hifi/config-spec x))
                       (if-let [name (some-> (:hifi/config-spec x) m/schema m/properties :name)]
                         (assoc x :hifi/config-spec name)
                         (dissoc x :hifi/config-spec))
                       x))
                   sys)]
      (puget/pprint (hifi.config/unmask cleaned)))
    (prn "HTTP")
    #_(prn
       ((get-in sys [::ds/defs :hifi/web :hifi.web/root-key :donut.system/start]) {})))

(defn load-system [opts]
  (let [config-loader (require-config-loader (:config-loader opts))]
    (try
      (-> (config-loader opts)
          (system/build-system))
      (catch Exception e
        (trove/log! {:error e :level :error :msg "Failed to build system"})
        (prn e)))))

(defn start
  "Start the application"
  [opts]
  (try
    (let [s (ds/start (load-system opts))]
      (reset! running-system_ s)
      (shutdown/add-shutdown-hook! ::stop stop)
      s)
    (catch Exception e
      (trove/log! {:error e :level :error :msg "Failed to start"})
      (prn e)
      ;; TODO nicely print validation errors in dev mode
      ;;(prn "value")
      ;;(prn (get-in (ex-data e) [:explanation :value]))
      ;;(prn "errors")
      ;;(prn (get-in (ex-data e) [:explanation :errors]))
      )))
