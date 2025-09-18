;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets.spec
  "Malli schemas for asset pipeline configuration validation.")

(def AssetConfigSchema
  [:map {:name :hifi/assets}
   [:hifi.assets/project-root {:doc "The project root dir to which all relative paths are resolved" :default "."} :string]
   [:hifi.assets/paths {:doc     "Asset source directories to scan"
                        :default ["assets"]} [:vector :string]]
   [:hifi.assets/excluded-paths {:doc     "Asset paths to exclude from scanning"
                                 :default []} [:vector :string]]
   [:hifi.assets/output-dir {:doc     "Output directory for compiled/digested assets"
                             :default "target/resources/public/assets"} :string]
   [:hifi.assets/manifest-path {:doc     "Path to the asset manifest EDN file"
                                :default "target/resources/public/assets/manifest.edn"} :string]])
