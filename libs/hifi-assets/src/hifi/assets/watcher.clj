(ns hifi.assets.watcher
  (:require
   [clojure.string :as str]
   [hifi.assets.beholder :as beholder]
   [hifi.assets.config :as config]
   [hifi.assets.pipeline :as pipeline]
   [hifi.util.timer :as timer]))

(defn has-extension?
  "Return true when `path` ends with extension `ext`, case-insensitive."
  [path ext]
  (str/ends-with? (str/lower-case (str path))
                  (str/lower-case ext)))

(defn -start
  "Create a watcher over `paths` and forward filtered events to `change-callback`."
  [{::keys [callback paths extensions beholder] :as opts}]
  (assert (sequential? paths))
  (assert callback)
  (let [callback (timer/debounce
                  (fn [{:keys [path] :as c}]
                    (try
                      (when (or (empty? extensions)
                                (some (partial has-extension? path) extensions))
                        (callback c opts))
                      (catch Throwable t
                        (tap> [:hifi-assets-watcher t])
                        (println t))))
                  {:wait 250
                   :leading? false
                   :trailing? true
                   :max-wait 2000})]
    (beholder/watch callback paths beholder)))

(defn start [config]
  (let [project-root (-> config :hifi.assets/config :hifi.assets/project-root)
        watch-paths (map #(config/project-path project-root %) (::paths config))]
    (-start
     (assoc config
            ::callback (fn [& args] (apply pipeline/watcher-callback (:hifi.assets/config config) args))
            ::paths watch-paths))))

(defn stop
  "Stop a watcher `instance` started via `start`."
  [instance]
  (beholder/stop instance))

(comment
  (def w (start {:paths ["/home/ramblurr/src/clojure-playground/hyperlith-is-for-crud/libs"] :change-callback #(tap> [:change %1 %2])}))
  (stop w)
  ;;
  )
