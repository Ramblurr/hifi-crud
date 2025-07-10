(ns hifi.dev-tasks.dev
  (:require
   [hifi.dev-tasks.util :refer [info error]]
   [babashka.process :as p]
   [babashka.fs :as fs]
   [babashka.tasks :refer [clojure]]
   [hifi.dev-tasks.css.tailwind :as tailwind]
   [hifi.dev-tasks.config :as config]))

(defn start-datomic []
  (info "[datomic] starting datomic in the background")
  (fs/create-dirs "datomic")
  (let [datomic-process (p/process
                         {:out      :write
                          :out-file (fs/file "datomic/dev.log")
                          :shutdown p/destroy-tree
                          :err      :out}
                         "bb" "datomic" "up")]
    (future
      (let [{:keys [exit]} @datomic-process]
        (if (zero? exit)
          (info "[datomic] started successfully")
          (error "[datomic] failed to start, check datomic/dev.log for details"))))))

(defn- start-background-tasks
  []
  (let [enabled-services (config/enabled-services)
        enabled?         (fn [s] (contains? enabled-services s))]
    (when (enabled? :datomic)
      (start-datomic)
      ;; TODO a proper way to wait for datomic to be ready
      (Thread/sleep 5000)))
  (when (tailwind/using-tailwind?)
    (future
      (tailwind/start-tailwind))))

(defn -main
  "Starts the application locally in development mode."
  [& args]
  (let [{:keys [main-ns]} (config/project-meta)
        safe-mode?        (some #{"--safe-mode"} args)]
    (fs/create-dirs "target/resources/public")
    (if safe-mode?
      (do
        (info "Running in safe mode, will not run dev tasks (css, etc) nor load the dev namespace.")
        (clojure "-M:dev"))
      (do
        (start-background-tasks)
        (clojure "-M:dev" "-e" (format "(dev) ((requiring-resolve (symbol \"%s\" \"-main\")))"
                                       (str main-ns)))))))
