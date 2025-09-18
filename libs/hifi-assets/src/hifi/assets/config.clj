;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets.config
  "Configuration loading and validation for the asset pipeline."
  (:require
   [babashka.fs :as fs]
   [hifi.assets.spec :as spec]
   [hifi.error.iface :as pe]))

(defn -validate-config
  "Validates configuration using Malli schema and coerces with defaults."
  [config]
  (try
    (-> (pe/coerce! spec/AssetConfigSchema (or config {}))
        (update :hifi.assets/project-root fs/canonicalize))
    (catch clojure.lang.ExceptionInfo e
      (if (pe/id? e ::pe/schema-validation-error)
        (do
          (pe/bling-schema-error e)
          (throw (ex-info "Asset pipeline configuration validation failed"
                          {:config config}
                          e)))
        (throw e)))))

(defn load-config
  "Loads and validates configuration in one step."
  [config]
  (-validate-config config))
