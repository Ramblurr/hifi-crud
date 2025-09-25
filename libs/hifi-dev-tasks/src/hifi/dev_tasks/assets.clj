;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.assets
  "Asset precompilation tasks for the HIFI asset pipeline"
  (:require
   [hifi.assets.config :as config]
   [hifi.assets.pipeline :as pipeline]
   [hifi.dev-tasks.config :as dev-config]))

(defn -main
  [& args]
  (let [config (-> (dev-config/read-config) :hifi/assets (config/load-config))]
    (case (first args)
      "clean"      (pipeline/clean config)
      "precompile" (pipeline/precompile config)
      (pipeline/precompile config))))
