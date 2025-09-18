;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets.scanner
  "Asset directory scanning functionality."
  (:require
   [babashka.fs :as fs]
   [medley.core :as medley]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn directory-exists?
  "Checks if a directory exists."
  [path]
  (let [file (io/file path)]
    (and (fs/exists? file) (fs/directory? file))))

(defn should-exclude?
  "Checks if a file path should be excluded based on exclusion patterns and default exclusions."
  [file-path excluded-paths]
  (boolean (some #(fs/starts-with? file-path %) excluded-paths)))

(defn collect-files
  "Recursively collects all files from `assets-path` building paths relative to `relative-root`"
  [assets-path relative-root]
  (let [dir (fs/file (fs/path relative-root assets-path))]
    (when (.exists dir)
      (->> (file-seq dir)
           (filter #(.isFile %))
           (remove fs/hidden?)
           (map (fn [file]
                  (let [abs-path              (fs/absolutize file)
                        project-relative-path (fs/relativize relative-root abs-path)
                        logical-path          (fs/relativize dir (fs/path file))]
                    {:file          file
                     :abs-path      (str abs-path)
                     :relative-path (str project-relative-path)
                     :logical-path  (str logical-path)})))))))

(defn scan-assets
  "Scans configured asset paths and returns a vector of asset file maps:

   {:file java.io.File
    :abs-path absolute string
    :relative-path string (project-root relative)
    :logical-path string}

   When multiple paths contain files with the same logical path, the file from
   the first path in the list takes precedence (e.g., if both 'assets/' and
   'lib/assets/' contain 'js/app.js', the one from 'assets/' wins when 'assets/'
   appears first in the paths list)."
  ([config]
   (scan-assets (:hifi.assets/paths config) config))
  ([asset-paths {:hifi.assets/keys [excluded-paths project-root]}]
   (assert project-root)
   (assert excluded-paths)
   (let [project-root   (fs/canonicalize project-root)
         excluded-paths (map (fn [p] (if (fs/absolute? p) p (fs/path project-root p)))
                             excluded-paths)
         xf             (comp
                         (distinct)
                         (filter directory-exists?)
                         (mapcat #(collect-files % project-root))
                         (remove #(should-exclude? (:abs-path %) excluded-paths))
                         (medley/distinct-by :logical-path))]
     (transduce xf conj [] asset-paths))))

(defn group-by-extension
  "Groups scanned assets by file extension."
  [scanned-assets]
  (group-by #(or (fs/extension (:file %)) "")
            scanned-assets))

(defn filter-by-extension
  "Filters scanned assets by file extension(s)."
  [scanned-assets extensions]
  (let [ext-set (set extensions)]
    (filter #(ext-set (fs/extension (:file %)))
            scanned-assets)))

(defn asset-info
  "Returns detailed information about a scanned asset file."
  [scanned-asset]
  (let [{:keys [file abs-path relative-path logical-path]} scanned-asset]
    {:file          file
     :abs-path      abs-path
     :relative-path relative-path
     :logical-path  logical-path
     :filename      (.getName file)
     :size          (.length file)
     :last-modified (.lastModified file)
     :extension     (let [name (.getName file)]
                      (when-let [dot-index (str/last-index-of name ".")]
                        (subs name (inc dot-index))))}))

(defn find-asset
  "Finds a specific asset by logical path in the scanned assets."
  [scanned-assets logical-path]
  (first (filter #(= (:logical-path %) logical-path) scanned-assets)))
