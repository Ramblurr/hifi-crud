(ns hifi.dev-tasks.dev
  (:require
   [babashka.fs :as fs]
   [babashka.tasks :refer [clojure]]
   [hifi.dev-tasks.css.tailwind :as tailwind]
   [hifi.dev-tasks.config :as config]))

(defn- start-background-tasks
  []
  (when (tailwind/using-tailwind?)
    (future (tailwind/watch-dev nil))))

(defn -main
  "Starts the application locally in development mode."
  [& args]
  (let [{:keys [main-ns]} (config/project-meta)
        safe-mode?        (some #{"--safe-mode"} args)]
    (fs/create-dirs "target/resources/public")
    (if safe-mode?
      (do
        (println "Running in safe mode, will not run dev tasks (css, etc) nor load the dev namespace.")
        (clojure "-M:dev"))
      (do
        (start-background-tasks)
        (clojure "-M:dev" "-e" "(dev)")))))
