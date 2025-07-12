;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2 

 (ns hifi.assets.spec
   "Malli schemas for asset pipeline configuration validation.")

(def AssetConfigSchema
  [:map {:name :hifi/assets}
   [:paths {:doc "Asset source directories to scan"
            :default ["assets"]} [:vector :string]]
   [:excluded-paths {:doc "Asset paths to exclude from scanning"
                     :default []} [:vector :string]]
   [:output-path {:doc "Output directory for compiled/digested assets"
                  :default "target/resources/public/assets"} :string]
   [:manifest-path {:doc "Path to the asset manifest EDN file"
                    :default "target/resources/public/assets/manifest.edn"} :string]
   [:base-url {:doc "Base URL prefix for assets"
               :default ""} :string]])