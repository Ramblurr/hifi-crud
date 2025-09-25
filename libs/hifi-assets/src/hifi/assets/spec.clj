;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets.spec
  "Malli schemas for asset pipeline configuration validation.")

(def AssetProcessor
  [:map
   [:mime-types {:doc "Set of MIME types handled by this processor."} [:set :string]]
   [:dependencies {:doc "Function that returns discovered logical dependencies for the asset."} fn?]
   [:process {:doc "Function that transforms asset content and returns the processed result."} fn?]])

(def AssetConfigSchema
  [:map {:name :hifi/assets}
   [:hifi.assets/verbose? {:doc "Controls how verbose the pipeline output (to stdout) is" :default true} :boolean]
   [:hifi.assets/project-root {:doc "The project root dir to which all relative paths are resolved" :default "."} :string]
   [:hifi.assets/paths {:doc "Asset source directories to scan"
                        :default ["assets"]} [:vector :string]]
   [:hifi.assets/excluded-paths {:doc "Asset paths to exclude from scanning"
                                 :default []} [:vector :string]]
   [:hifi.assets/output-dir {:doc "Output directory for compiled/digested assets"
                             :default "target/resources/public/assets"} :string]
   [:hifi.assets/manifest-path {:doc "Path to the asset manifest EDN file"
                                :default "target/resources/public/assets/manifest.edn"} :string]
   [:hifi.assets/prefix {:doc "URL prefix for serving assets"
                         :default "/assets"} :string]
   [:hifi.assets/processors {:doc "Asset processors for transforming content"
                             :default []} [:vector AssetProcessor]]])

(def BeholderOptions
  [:map {:name :beholder}
   [:file-hasher {:doc "Used by beholder to prevent duplicate events. :slow is only recomended on filesystems that do not have milliscond precision or better"
                  :default :last-modified} [:enum :slow :last-modified]]])

(def AssetsWatcherComponentOptions
  [:map {:name :hifi.assets/watcher}
   [:hifi.assets.watcher/enable? {:doc "Controls whether the assets watcher starts automatically. When omitted, defaults to enabled in development mode and disabled otherwise." :optional true} [:maybe :boolean]]
   [:hifi.assets.watcher/paths {:doc "Directories to monitor for asset changes." :default ["assets"]} [:vector :string]]
   [:hifi.assets.watcher/extensions {:doc "File extensions to include; an empty set watches all files." :default #{}} [:set :string]]
   [:hifi.assets.watcher/beholder {:doc "Options forwarded to the underlying beholder watcher." :default {}} BeholderOptions]
   [:hifi.assets.watcher/callback {:doc "Function invoked with each filtered change event." :optional true} fn?]])
