;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2 

 (ns hifi.assets.scanner
   "Asset directory scanning functionality."
   (:require
    [clojure.java.io :as io]
    [clojure.string :as str]))

(defn- directory-exists?
  "Checks if a directory exists."
  [path]
  (let [file (io/file path)]
    (and (.exists file) (.isDirectory file))))

(defn- should-exclude?
  "Checks if a file path should be excluded based on exclusion patterns."
  [file-path excluded-paths]
  (boolean (some #(str/starts-with? file-path %) excluded-paths)))

(defn- collect-files
  "Recursively collects all files from a directory."
  [dir-path relative-root]
  (let [dir (io/file dir-path)]
    (when (.exists dir)
      (->> (file-seq dir)
           (filter #(.isFile %))
           (map (fn [file]
                  (let [full-path (.getAbsolutePath file)
                        ;; Get path relative to the dir-path, not relative-root
                        relative-to-dir (str/replace full-path
                                                     (str (.getAbsolutePath dir) "/")
                                                     "")]
                    {:file file
                     :full-path full-path
                     :relative-path (str/replace full-path
                                                 (str (.getAbsolutePath (io/file relative-root)) "/")
                                                 "")
                     :logical-path relative-to-dir})))))))

(defn scan-asset-paths
  "Scans configured asset paths and returns a collection of asset file information.
   
   Returns a vector of maps with:
   {:file - Java File object
    :full-path - Absolute path to file
    :relative-path - Path relative to project root
    :logical-path - Logical asset path (e.g., 'app.js')}"
  [asset-paths excluded-paths]
  (let [existing-paths (filter directory-exists? asset-paths)]
    (when (seq existing-paths)
      (->> existing-paths
           (mapcat #(collect-files % "."))
           (remove #(should-exclude? (:relative-path %) excluded-paths))
           (vec)))))

(defn scan-assets-from-config
  "Convenience function to scan assets using configuration map."
  [config]
  (let [asset-paths (get-in config [:hifi/assets :paths] ["assets"])
        excluded-paths (get-in config [:hifi/assets :excluded-paths] [])]
    (scan-asset-paths asset-paths excluded-paths)))

(defn group-by-extension
  "Groups scanned assets by file extension."
  [scanned-assets]
  (group-by #(let [name (.getName (:file %))]
               (if-let [dot-index (str/last-index-of name ".")]
                 (subs name (inc dot-index))
                 ""))
            scanned-assets))

(defn filter-by-extension
  "Filters scanned assets by file extension(s)."
  [scanned-assets extensions]
  (let [ext-set (set extensions)]
    (filter #(let [name (.getName (:file %))
                   ext (when-let [dot-index (str/last-index-of name ".")]
                         (subs name (inc dot-index)))]
               (ext-set ext))
            scanned-assets)))

(defn asset-info
  "Returns detailed information about a scanned asset file."
  [scanned-asset]
  (let [{:keys [file full-path relative-path logical-path]} scanned-asset]
    {:file file
     :full-path full-path
     :relative-path relative-path
     :logical-path logical-path
     :filename (.getName file)
     :size (.length file)
     :last-modified (.lastModified file)
     :extension (let [name (.getName file)]
                  (when-let [dot-index (str/last-index-of name ".")]
                    (subs name (inc dot-index))))}))

(defn find-asset
  "Finds a specific asset by logical path in the scanned assets."
  [scanned-assets logical-path]
  (first (filter #(= (:logical-path %) logical-path) scanned-assets)))