;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

 (ns hifi.assets.manifest
   "Manifest generation and loading for the asset pipeline."
   (:require
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]))

(defn create-manifest-entry
  "Creates a manifest entry from digest information."
  [digest-info]
  (let [{:keys [digest-name sri-hash size logical-path]} digest-info]
    {logical-path {:digest-path digest-name
                   :integrity sri-hash
                   :size size
                   :last-modified (str (java.time.Instant/now))}}))

(defn generate-manifest
  "Generates a complete manifest from a collection of digest-info maps."
  [digest-infos]
  (reduce (fn [manifest digest-info]
            (merge manifest (create-manifest-entry digest-info)))
          {}
          digest-infos))

(defn write-manifest
  "Writes the manifest to a file in EDN format."
  [manifest output-dir]
  (let [output-file (io/file output-dir)]
    (io/make-parents output-file)
    (with-open [writer (io/writer output-file)]
      (pprint/write manifest :stream writer :pretty true))))

(defn load-manifest
  "Loads a manifest from an EDN file. Returns empty map if file doesn't exist."
  [manifest-path]
  (let [manifest-file (io/file manifest-path)]
    (if (.exists manifest-file)
      (try
        (edn/read-string (slurp manifest-file))
        (catch Exception e
          (throw (ex-info "Failed to load manifest file"
                          {:path manifest-path
                           :error (.getMessage e)}
                          e))))
      {})))

(defn manifest-lookup
  "Looks up an asset path in the manifest and returns the digested path."
  [manifest logical-path]
  (get-in manifest [logical-path :digest-path]))

(defn manifest-integrity
  "Looks up the integrity hash for an asset in the manifest."
  [manifest logical-path]
  (get-in manifest [logical-path :integrity]))

(defn manifest-contains?
  "Checks if the manifest contains an entry for the given logical path."
  [manifest logical-path]
  (contains? manifest logical-path))

(defn update-manifest-entry
  "Updates or adds a single entry to an existing manifest."
  [manifest digest-info]
  (merge manifest (create-manifest-entry digest-info)))
