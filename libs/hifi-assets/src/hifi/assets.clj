;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets
  "Asset pipeline for HIFI framework - provides digest-based asset management,
   HTML helpers, and development/production asset serving."
  (:require
   [clojure.java.io :as io]
   [hifi.assets.config :as config]
   [hifi.assets.manifest :as manifest]
   [hifi.html.protocols :as html.p]))

(defn create-asset-context
  "Creates an asset context map containing configuration and manifest data.
   This context is passed to all asset functions.

   Options:
   - :config - Assets configuration map"
  ([opts]
   (let [config        (config/load-config (:config opts))
         manifest-path (:hifi.assets/manifest-path config)
         manifest      (manifest/load-manifest manifest-path)]
     {:config        config
      :manifest      manifest
      :manifest-path manifest-path}))
  ([]
   (create-asset-context {})))

(defn asset-path
  "Returns the digested path to an asset from the manifest.
   Falls back to the logical path if not found in manifest."
  [asset-ctx logical-path]
  (or (get-in (:manifest asset-ctx) [logical-path :digest-path])
      logical-path))

(defn asset-integrity
  "Returns the SRI (Subresource Integrity) hash for an asset.
   Returns nil if no integrity hash exists in the manifest."
  [asset-ctx logical-path]
  (get-in (:manifest asset-ctx) [logical-path :integrity]))

(defn asset-exists?
  "Checks if an asset exists in the manifest."
  [asset-ctx logical-path]
  (contains? (:manifest asset-ctx) logical-path))

(defn asset-read
  "Returns an InputStream for the asset's compiled bytes from the output directory."
  [asset-ctx logical-path]
  (when-let [digest-path (get-in (:manifest asset-ctx) [logical-path :digest-path])]
    (let [output-dir (get-in asset-ctx [:config :hifi.assets/output-dir])
          file-path  (str output-dir "/" digest-path)
          file       (io/file file-path)]
      (when (.exists file)
        (io/input-stream file)))))

(defn asset-locate
  "Returns a java.nio.file.Path to the compiled file on disk in the output directory."
  [asset-ctx logical-path]
  (when-let [digest-path (get-in (:manifest asset-ctx) [logical-path :digest-path])]
    (let [output-dir (get-in asset-ctx [:config :hifi.assets/output-dir])
          file-path  (str output-dir "/" digest-path)
          file       (io/file file-path)]
      (when (.exists file)
        (.toPath file)))))

(defrecord HifiAssetResolver [asset-ctx]
  html.p/AssetResolver
  (resolve-path [_ logical-path]
    (asset-path asset-ctx logical-path))
  (integrity [_ logical-path]
    (asset-integrity asset-ctx logical-path))
  (read-bytes [_ logical-path]
    (asset-read asset-ctx logical-path))
  (locate [_ logical-path]
    (asset-locate asset-ctx logical-path)))

(defn assets-resolver
  "Creates a HifiAssetResolver that implements the AssetResolver protocol using hifi-assets.

  Arguments:
  - asset-ctx: The asset context containing manifest and configuration"
  [asset-ctx]
  (->HifiAssetResolver asset-ctx))
