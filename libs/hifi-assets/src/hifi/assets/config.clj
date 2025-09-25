;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets.config
  "Configuration loading and validation for the asset pipeline."
  (:require
   [babashka.fs :as fs]
   [hifi.assets.processors :as processors]
   [hifi.assets.spec :as spec]
   [hifi.error.iface :as pe]))

(defn project-path
  "Given a project root directory and a file path, return a string
   representing the absolute path. If `path` is already absolute,
   return it unchanged; otherwise, return it joined to `project-root`."
  [project-root path]
  (let [root-p (fs/path project-root)
        p (fs/path path)]
    (if (fs/absolute? p)
      (str p)
      (str (fs/path root-p p)))))

(defn -validate-config
  "Validates configuration using Malli schema and coerces with defaults."
  [config]
  (try
    (as-> (pe/coerce! spec/AssetConfigSchema (or config {})) $
      (update $ :hifi.assets/project-root (comp str fs/canonicalize))
      (update $ :hifi.assets/processors
              (fn [processors]
                (if (empty? processors)
                  processors/default-processors
                  processors)))
      (update $ :hifi.assets/output-dir #(project-path (:hifi.assets/project-root $) %))
      (update $ :hifi.assets/manifest-path #(project-path (:hifi.assets/project-root $) %)))
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
