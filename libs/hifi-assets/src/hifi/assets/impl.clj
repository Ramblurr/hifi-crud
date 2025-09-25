;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns ^:no-doc hifi.assets.impl
  (:require
   [clojure.java.io :as io]
   [hifi.assets.config :as config]
   [hifi.assets.manifest :as manifest]
   [hifi.html.protocols :as html.p]))

(defn create-asset-context
  [opts]
  (let [config        (config/load-config opts)
        manifest-path (:hifi.assets/manifest-path config)
        manifest      (manifest/load-manifest manifest-path)]
    {:config        config
     :manifest      manifest
     :manifest-path manifest-path}))

(defn asset-path
  [asset-ctx logical-path]
  (or (get-in (:manifest asset-ctx) [logical-path :digest-path])
      logical-path))

(defn asset-url [asset-ctx logical-path]
  (str (get-in asset-ctx [:config :hifi.assets/prefix]) "/" (asset-path asset-ctx logical-path)))

(defn asset-integrity
  [asset-ctx logical-path]
  (get-in (:manifest asset-ctx) [logical-path :integrity]))

(defn asset-exists?

  [asset-ctx logical-path]
  (contains? (:manifest asset-ctx) logical-path))

(defn asset-read
  [asset-ctx logical-path]
  (when-let [digest-path (get-in (:manifest asset-ctx) [logical-path :digest-path])]
    (let [output-dir (get-in asset-ctx [:config :hifi.assets/output-dir])
          file-path  (str output-dir "/" digest-path)
          file       (io/file file-path)]
      (when (.exists file)
        (io/input-stream file)))))

(defn asset-locate
  [asset-ctx logical-path]
  (when-let [digest-path (get-in (:manifest asset-ctx) [logical-path :digest-path])]
    (let [output-dir (get-in asset-ctx [:config :hifi.assets/output-dir])
          file-path  (str output-dir "/" digest-path)
          file       (io/file file-path)]
      (when (.exists file)
        (.toPath file)))))

(defrecord StaticHifiAssetResolver [asset-ctx]
  html.p/AssetResolver
  (resolve-path [_ logical-path]
    (asset-url asset-ctx logical-path))
  (integrity [_ logical-path]
    (asset-integrity asset-ctx logical-path))
  (read-bytes [_ logical-path]
    (asset-read asset-ctx logical-path))
  (locate [_ logical-path]
    (asset-locate asset-ctx logical-path)))

(defrecord DynamicHifiAssetResolver [assets-config]
  html.p/AssetResolver
  (resolve-path [_ logical-path]
    (asset-url (create-asset-context assets-config) logical-path))
  (integrity [_ logical-path]
    (asset-integrity (create-asset-context assets-config) logical-path))
  (read-bytes [_ logical-path]
    (asset-read (create-asset-context assets-config) logical-path))
  (locate [_ logical-path]
    (asset-locate (create-asset-context assets-config) logical-path)))

(defn static-assets-resolver
  [asset-ctx]
  (->StaticHifiAssetResolver asset-ctx))

(defn dynamic-assets-resolver
  [assets-config]
  (->DynamicHifiAssetResolver assets-config))
