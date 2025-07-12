;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2 

 (ns hifi.assets.config
   "Configuration loading and validation for the asset pipeline."
   (:require
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [medley.core :as m]
    [hifi.assets.spec :as spec]
    [hifi.error.iface :as pe]))

(def default-config
  "Default configuration for the asset pipeline."
  {:hifi/assets {:paths ["assets"]
                 :excluded-paths []
                 :output-path "target/resources/public/assets"
                 :manifest-path "target/resources/public/assets/manifest.edn"
                 :base-url ""}})

(defn load-config
  "Loads configuration from various sources.
   
   config can be:
   - nil: Uses default configuration
   - string: Path to EDN file
   - map: Configuration map to use directly"
  [config]
  (cond
    (nil? config)
    default-config

    (string? config)
    (if (.exists (io/file config))
      (m/deep-merge default-config (edn/read-string (slurp config)))
      (throw (ex-info "Configuration file not found" {:path config})))

    (map? config)
    (m/deep-merge default-config config)

    :else
    (throw (ex-info "Invalid configuration type" {:config config :type (type config)}))))

(defn validate-config
  "Validates configuration using Malli schema and coerces with defaults."
  [config]
  (try
    (pe/coerce! spec/AssetConfigSchema (get config :hifi/assets {}))
    (catch clojure.lang.ExceptionInfo e
      (if (pe/id? e ::pe/schema-validation-error)
        (do
          (pe/bling-schema-error e)
          (throw (ex-info "Asset pipeline configuration validation failed"
                          {:config config}
                          e)))
        (throw e)))))

(defn load-and-validate-config
  "Loads and validates configuration in one step."
  [config]
  (let [loaded-config (load-config config)
        validated-assets (validate-config loaded-config)]
    (assoc loaded-config :hifi/assets validated-assets)))

(defn get-asset-paths
  "Returns the configured asset paths."
  [config]
  (get-in config [:hifi/assets :paths] ["assets"]))

(defn get-excluded-paths
  "Returns the configured excluded paths."
  [config]
  (get-in config [:hifi/assets :excluded-paths] []))

(defn get-output-path
  "Returns the configured output path for compiled assets."
  [config]
  (get-in config [:hifi/assets :output-path] "target/resources/public/assets"))

(defn get-manifest-path
  "Returns the configured manifest file path."
  [config]
  (get-in config [:hifi/assets :manifest-path] "target/resources/public/assets/manifest.edn"))