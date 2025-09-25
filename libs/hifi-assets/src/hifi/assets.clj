;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets
  "Asset pipeline for HIFI framework - provides digest-based asset management,
   HTML helpers, and development/production asset serving."
  (:require
   [hifi.config :as config]
   [hifi.assets.spec :as spec]
   [hifi.assets.config :as assets.config]
   [hifi.assets.watcher :as watcher]
   [hifi.assets.impl :as impl]
   [hifi.assets.middleware :as middleware]
   [hifi.core :as h]))

(defn create-asset-context
  "Creates an asset context map containing configuration and manifest data.
   This context is passed to all asset functions.

   Options:
   - :config - Assets configuration map"
  ([opts]
   (impl/create-asset-context opts))
  ([]
   (impl/create-asset-context {})))

(defn asset-path
  "Returns the digested path to an asset from the manifest.
   Falls back to the logical path if not found in manifest."
  [asset-ctx logical-path]
  (impl/asset-path asset-ctx logical-path))

(defn asset-integrity
  "Returns the SRI (Subresource Integrity) hash for an asset.
   Returns nil if no integrity hash exists in the manifest."
  [asset-ctx logical-path]
  (impl/asset-integrity asset-ctx logical-path))

(defn asset-exists?
  "Checks if an asset exists in the manifest."
  [asset-ctx logical-path]
  (impl/asset-exists? asset-ctx logical-path))

(defn asset-read
  "Returns an InputStream for the asset's compiled bytes from the output directory."
  [asset-ctx logical-path]
  (impl/asset-read asset-ctx logical-path))

(defn asset-locate
  "Returns a java.nio.file.Path to the compiled file on disk in the output directory."
  [asset-ctx logical-path]
  (impl/asset-locate asset-ctx logical-path))

(defn static-assets-resolver
  "Creates a static HifiAssetResolver that resolves assets from a static manifest"
  [assets-config]
  (impl/static-assets-resolver (create-asset-context assets-config)))

(defn dynamic-assets-resolver
  "Creates a HifiAssetResolver that resolves assets and always reloads the manifest "
  [assets-config]
  (impl/dynamic-assets-resolver assets-config))

(h/defcomponent AssetsConfigComponent
  "Holds the configuration for the assets pipeline under the :hifi/assets key"
  {:donut.system/start (fn [{:donut.system/keys [config]}]
                         (assets.config/load-config config))
   :hifi/config-spec spec/AssetConfigSchema
   :hifi/config-key :hifi/assets})

(h/defcomponent AssetsWatcherComponent
  "Watches for changes in the watch paths and triggers a assets pipeline build. "
  {:donut.system/start  (fn  [{:donut.system/keys [config]}]
                          (when (if (nil? (::watcher/enable? config)) (config/dev?) (::watcher/enable? config))
                            (watcher/start config)))
   :donut.system/stop   (fn [{instance :donut.system/instance}]
                          (when instance
                            (watcher/stop instance)))
   :donut.system/config {:hifi.assets/config [:donut.system/local-ref [:hifi.assets/config]]}
   :hifi/config-spec    spec/AssetsWatcherComponentOptions
   :hifi/config-key     :hifi/assets})

(h/defcomponent AssetsResolverComponent
  "Provides a [[hifi.html.protocols/AssetResolver]] that resolves asset content and data"
  {:donut.system/start  (fn [{:donut.system/keys [config]}]
                          (if (config/dev?)
                            (dynamic-assets-resolver (:hifi.assets/config config))
                            (static-assets-resolver (:hifi.assets/config config))))
   :donut.system/config {:hifi.assets/config [:donut.system/local-ref [:hifi.assets/config]]}})

(h/defplugin Pipeline
  "The hifi assets pipeline: dev-time watching and building of assets, resolving them with hiccup helpers, and serving cache-friendly assets via middleware."
  {:hifi/assets
   {:hifi.assets/watcher  AssetsWatcherComponent
    :hifi.assets/resolver AssetsResolverComponent
    :hifi.assets/config   AssetsConfigComponent}
   :hifi/middleware
   {:hifi/assets                 middleware/AssetsMiddlewareComponent
    :hifi/assets-static-resolve  middleware/StaticAssetsMiddlewareComponent
    :hifi/assets-dynamic-resolve middleware/DynamicAssetsMiddlewareComponent
    :hifi/assets-resolver        middleware/AssetsResolverMiddlewareComponent}})
