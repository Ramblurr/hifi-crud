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

(defn- copy-digested-file!
  "Copies a file to the output directory with its digested name"
  [asset digest-info output-dir]
  (let [source-path (:abs-path asset)
        target-path (str output-dir "/" (:digest-name digest-info))
        target-file (io/file target-path)]
    (fs/create-dirs (fs/parent target-file))
    (fs/copy source-path target-path {:replace-existing true})))

(defn print-compile-head [{:hifi.assets/keys [paths excluded-paths output-dir]}]
  (println "Asset Precompilation")
  (println "====================")
  (println "Asset paths:" (str/join ", " paths))
  (when (seq excluded-paths)
    (println "Excluded:" (str/join ", " excluded-paths)))
  (println "Output:" output-dir)
  (println)
  (println "Scanning assets..."))

(defn print-scan-result [assets]
  (if (zero? (count assets))
    (println "No assets found to process")
    (println (str "Found " (count assets) " assets"))))

(defn print-manifest-result [manifest-path manifest-data digest-infos]
  (println (str "Wrote manifest to " manifest-path))
  (println "\n✓ Precompiled" (count digest-infos) "assets")
  (doseq [[logical-path entry] manifest-data]
    (println "  " logical-path "→" (:digest-path entry))))

(defn precompile
  "Precompiles assets by scanning, digesting, copying to target, and generating manifest.

   Options:
   - :output-dir - Output directory (default: target/resources/public/assets)
   - :manifest-path - Path to manifest file (default: <output-dir>/manifest.edn)
   - :verbose - Print detailed output"
  ([]
   (precompile {}))
  ([opts]
   (let [{:as config :hifi.assets/keys [manifest-path  output-dir]} (config/load-config (:hifi/assets (dev-config/read-config)))
         verbose?       (:verbose opts true)]
     (when verbose? (print-compile-head config))
     (let [assets (scanner/scan-assets config)]
       (when verbose?
         (print-scan-result assets))
       (when (zero? (count assets))
         (System/exit 0))

       (fs/create-dirs output-dir)
       (when verbose?
         (println "\nProcessing assets..."))
       (let [digest-infos (mapv (fn [asset]
                                  (let [digest-info (digest/digest-file-content
                                                     (:abs-path asset)
                                                     (:logical-path asset))]
                                    (copy-digested-file! asset digest-info output-dir)
                                    (when verbose?
                                      (println "  " (str (:logical-path asset)) "→" (:digest-name digest-info)))
                                    digest-info))
                                assets)]

         (when verbose?
           (println "\nGenerating manifest..."))
         (let [manifest-data (manifest/generate-manifest digest-infos)]
           (manifest/write-manifest manifest-data manifest-path)
           (when verbose?
             (print-manifest-result manifest-path manifest-data digest-infos))))))))

(defn clean
  "Removes the compiled assets directory"
  ([]
   (clean {}))
  ([opts]
   (let [config (config/load-config (dev-config/read-config))
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
