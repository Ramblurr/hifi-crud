;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns ^:no-doc hifi.assets.digest
  "Asset hashing and digesting functionality."
  (:require
   [babashka.fs :as fs]
   [hifi.util.crypto :as crypto]))

(defn extract-existing-digest
  "Extracts digest from pre-digested filename if it follows the pattern:
   filename-[digest].digested.ext
   Returns [base-name digest extension] or nil if not pre-digested."
  [filename]
  (let [pattern #"^(.+)-([a-f0-9]{8,})\.digested\.(.+)$"
        match (re-matches pattern filename)]
    (when match
      [(nth match 1) (nth match 2) (nth match 3)])))

(defn is-pre-digested?
  "Checks if a filename follows the pre-digested pattern."
  [filename]
  (boolean (extract-existing-digest filename)))

(defn create-digested-filename
  "Creates a digested filename by inserting the hash before the extension.
   Returns filename-digest.ext"
  [filename digest]
  (let [last-dot (.lastIndexOf filename ".")
        base (if (pos? last-dot)
               (.substring filename 0 last-dot)
               filename)
        ext (if (pos? last-dot)
              (.substring filename last-dot)
              "")]
    (str base "-" (subs digest 0 8) ext)))

(defn digest-file-content
  "Returns a map with digest information for a file.

   Returns:
   {:original-name - Original filename
    :digest-name - Digested filename
    :sha256-hash - SHA-256 hash (for cache busting)
    :sri-hash - SHA-384 hash (for SRI)
    :pre-digested? - Whether file was already digested
    :size - File size in bytes
    :logical-path - Asset path relative to asset dir, used in helpers
  }"
  [file-path logical-path]
  (let [file      (fs/file file-path)
        filename  (.getName file)
        file-size (.length file)]

    (if-let [[base-name existing-digest ext] (extract-existing-digest filename)]
      ;; Pre-digested file
      {:original-name (str base-name "." ext)
       :digest-name   filename
       :sha256-hash   existing-digest
       :sri-hash      (crypto/sri-sha384-file file-path)
       :pre-digested? true
       :size          file-size
       :logical-path  logical-path}

      ;; Regular file - compute digest
      (let [sha256-hash (crypto/sha256-file-hex file-path)
            sri-hash    (crypto/sri-sha384-file file-path)
            digest-name (create-digested-filename filename sha256-hash)]
        {:original-name filename
         :digest-name   digest-name
         :sha256-hash   sha256-hash
         :sri-hash      sri-hash
         :pre-digested? false
         :size          file-size
         :logical-path  logical-path}))))

(defn short-digest
  "Returns a shortened version of the digest for use in filenames (first 8 chars)."
  [digest]
  (subs digest 0 8))
