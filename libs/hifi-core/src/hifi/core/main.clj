(ns hifi.core.main
  (:require
   [donut.system :as ds]
   [hifi.core.system :as system]
   [hifi.util.shutdown :as shutdown]
   [sys-ext.core :as se]
   [taoensso.trove :as trove]))

(defonce running-system_ (atom nil))

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

(defn build-system [config]
  (try
    (dissoc
     (->> (or (:hifi/plugins config) [])
          (system/resolve-plugins)
          (system/build-system config)
          (se/remove-dead-refs))
     :donut.system/plugins)
    (catch Exception e
      (throw (ex-info "Building the system failed" {} e)))))
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

(defn start
  "Start the application"
  [opts]
  (let [config-loader (require-config-loader (:config-loader opts))]
    (try
      (let [i (-> (config-loader opts)
                  (build-system))
            #_#__ (println "intermedia ")
            #_#__ (print-system i)
            #_#__ (println (keys i))
            s (ds/start i)]
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
        ))))
