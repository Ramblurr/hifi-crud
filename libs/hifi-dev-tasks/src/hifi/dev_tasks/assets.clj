;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

(ns hifi.dev-tasks.assets
  "Asset precompilation tasks for the HIFI asset pipeline"
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hifi.assets.config :as config]
   [hifi.assets.digest :as digest]
   [hifi.assets.manifest :as manifest]
   [hifi.assets.scanner :as scanner]
   [hifi.dev-tasks.config :as dev-config]))

(defn- ensure-output-dir!
  "Ensures the output directory exists"
  [output-dir]
  (fs/create-dirs output-dir))

(defn- copy-digested-file!
  "Copies a file to the output directory with its digested name"
  [asset digest-info output-dir]
  (let [source-path (:full-path asset)
        target-path (str output-dir "/" (:digest-name digest-info))
        target-file (io/file target-path)]
    (fs/create-dirs (fs/parent target-file))
    (fs/copy source-path target-path {:replace-existing true})
    (println "  " (:logical-path asset) "→" (:digest-name digest-info))))

(defn precompile
  "Precompiles assets by scanning, digesting, copying to target, and generating manifest.

   Options:
   - :output-dir - Output directory (default: target/resources/public/assets)
   - :manifest-path - Path to manifest file (default: <output-dir>/manifest.edn)
   - :verbose - Print detailed output"
  ([]
   (precompile {}))
  ([opts]
   (let [config         (config/load-and-validate-config (dev-config/read-config))
         asset-paths    (get-in config [:hifi/assets :paths] ["assets"])
         excluded-paths (get-in config [:hifi/assets :excluded-paths] [])
         output-dir     (or (:output-dir opts)
                            (get-in config [:hifi/assets :output-dir])
                            "target/resources/public/assets")
         manifest-path  (or (:manifest-path opts)
                            (str output-dir "/manifest.edn"))
         verbose?       (:verbose opts)]

     (println "Asset Precompilation")
     (println "====================")
     (println "Asset paths:" (str/join ", " asset-paths))
     (when (seq excluded-paths)
       (println "Excluded:" (str/join ", " excluded-paths)))
     (println "Output:" output-dir)
     (println)

     (println "Scanning assets...")
     (let [assets (scanner/scan-asset-paths asset-paths excluded-paths)]
       (println (str "Found " (count assets) " assets"))

       (when (zero? (count assets))
         (println "No assets found to process")
         (System/exit 0))

       (ensure-output-dir! output-dir)

       (println "\nProcessing assets...")
       (let [digest-infos (mapv (fn [asset]
                                  (let [digest-info (digest/digest-file-content
                                                     (:full-path asset)
                                                     (:logical-path asset))]
                                    (copy-digested-file! asset digest-info output-dir)
                                    digest-info))
                                assets)]

         (println "\nGenerating manifest...")
         (let [manifest-data (manifest/generate-manifest digest-infos)]
           (manifest/write-manifest manifest-data manifest-path)
           (println (str "Wrote manifest to " manifest-path))

           (println "\n✓ Precompiled" (count digest-infos) "assets")
           (when verbose?
             (doseq [[logical-path entry] manifest-data]
               (println "  " logical-path "→" (:digest-path entry))))))))))

(defn clean
  "Removes the compiled assets directory"
  ([]
   (clean {}))
  ([opts]
   (let [config (config/load-and-validate-config (dev-config/read-config))
         output-dir (or (:output-dir opts)
                        (get-in config [:hifi/assets :output-dir])
                        "target/resources/public/assets")]
     (println "Cleaning assets from" output-dir)
     (when (fs/exists? output-dir)
       (fs/delete-tree output-dir)
       (println "✓ Cleaned"))
     (println "Done"))))

(defn -main
  [& args]
  (case (first args)
    "clean" (clean)
    "precompile" (precompile {:verbose true})
    (precompile {:verbose true})))
