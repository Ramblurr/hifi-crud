;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.util.crypto
  "Cryptographic utilities for secure hashing and random value generation.

   Features:
   - Secure random bytes/strings with cryptographic randomness
   - SHA-256/384 hashing with multiple input types
   - HMAC-SHA256 for message authentication
   - SRI (Subresource Integrity) hash generation per W3C spec
   - Timing-attack resistant equality comparison

   Naming conventions:
   - Base functions (sha256, sha384) operate on byte arrays
   - -stream suffix for input streams
   - -file suffix for filesystem files
   - -resource suffix for classpath resources
   - -hex suffix for hex string output"
  (:refer-clojure :exclude [bytes])
  (:require
   [clojure.java.io :as io]
   [crypto.equality :as equality]
   [hifi.util.codec :as codec])
  (:import
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]
   [java.security SecureRandom MessageDigest]))

(def ^SecureRandom secure-random
  (SecureRandom/new))

(defn bytes
  "Returns secure random bytes of specified size."
  ^bytes [size]
  (let [seed (byte-array size)]
    (.nextBytes secure-random seed)
    seed))

(defn rand-string
  "Returns base64-encoded random string of n bytes."
  [n]
  (codec/->base64 (bytes n)))

(defn random-unguessable-uid
  "Returns URL-safe base64-encoded 160-bit (20 byte) random value.

   Prefer over UUIDs for cryptographically secure randomness.
   See: https://neilmadden.blog/2018/08/30/moving-away-from-uuids/"
  []
  (codec/->base64 (bytes 20)))

(def new-uid
  "Alias for random-unguessable-uid."
  random-unguessable-uid)

(def eq?
  "Timing-attack resistant equality comparison for strings or bytes.
   Note: Does not hide length information."
  equality/eq?)

(defn secret-key->hmac-sha256-keyspec
  "Converts string key to HMAC-SHA256 key specification."
  [^String secret-key]
  (SecretKeySpec. (.getBytes secret-key) "HmacSHA256"))

(defn hmac-sha256
  "Computes HMAC-SHA256 and returns base64-encoded result."
  [key-spec ^String data]
  (-> (doto (Mac/getInstance "HmacSHA256")
        (.init key-spec))
      (.doFinal (.getBytes data))
      codec/->base64))

(defn hash-stream-data
  "Returns MessageDigest after hashing stream with algorithm."
  ^MessageDigest [^String algo ^java.io.InputStream in]
  (when in
    (let [d (MessageDigest/getInstance algo)
          buffer (byte-array 5120)]
      (loop []
        (let [read-size (.read in buffer 0 5120)]
          (when-not (= read-size -1)
            (.update d buffer 0 read-size)
            (recur))))
      d)))

(defn hash-bytes
  "Hashes byte array with algorithm."
  ^bytes [^String algo ^bytes input-bytes]
  (.digest (hash-stream-data algo (io/input-stream input-bytes))))

(defn hash-file
  "Hashes file with algorithm. Returns nil if file doesn't exist."
  ^bytes [^String algo file-path]
  (let [file (io/file file-path)]
    (when (.exists file)
      (with-open [in (io/input-stream file)]
        (.digest (hash-stream-data algo in))))))

(defn hash-resource
  "Hashes classpath resource with algorithm."
  ^bytes [^String algo path]
  (if-let [resource (io/resource path)]
    (with-open [in (io/input-stream resource)]
      (.digest (hash-stream-data algo in)))
    (throw (ex-info "Cannot load resource from classpath" {:path path}))))

(defn sha384
  "Computes SHA-384 hash of byte array."
  ^bytes [^bytes input-bytes]
  (hash-bytes "SHA-384" input-bytes))

(defn sha384-stream
  "Computes SHA-384 hash of input stream."
  ^bytes [^java.io.InputStream in]
  (.digest (hash-stream-data "SHA-384" in)))

(defn sha384-resource
  "Computes SHA-384 hash of classpath resource."
  ^bytes [path]
  (hash-resource "SHA-384" path))

(defn sha256
  "Computes SHA-256 hash of byte array."
  ^bytes [^bytes input-bytes]
  (hash-bytes "SHA-256" input-bytes))

(defn sha256-stream
  "Computes SHA-256 hash of input stream."
  ^bytes [^java.io.InputStream in]
  (.digest (hash-stream-data "SHA-256" in)))

(defn sha256-file
  "Computes SHA-256 hash of file, returns nil if file doesn't exist."
  ^bytes [file-path]
  (hash-file "SHA-256" file-path))

(defn sha256-file-hex
  "Computes SHA-256 hash of file and returns hex string."
  [file-path]
  (when-let [hash-bytes (sha256-file file-path)]
    (codec/->hex hash-bytes)))

(defn sha384-file
  "Computes SHA-384 hash of file, returns nil if file doesn't exist."
  ^bytes [file-path]
  (hash-file "SHA-384" file-path))

(defn sha256-resource
  "Computes SHA-256 hash of classpath resource."
  ^bytes [path]
  (hash-resource "SHA-256" path))

(defn sha256-resource-hex
  "Computes SHA-256 hash of classpath resource and returns hex string."
  [path]
  (codec/->hex (sha256-resource path)))

(def ^:private sri-algorithm-map
  "Maps W3C SRI algorithm tokens to Java MessageDigest names.
   See: https://w3c.github.io/webappsec-subresource-integrity/#terms

   \"The valid SRI hash algorithm token set is the ordered set
   « \"sha256\", \"sha384\", \"sha512\" » (corresponding to SHA-256,
   SHA-384, and SHA-512 respectively).\""
  {"sha256" "SHA-256"
   "sha384" "SHA-384"
   "sha512" "SHA-512"})

(defn sri-hash-stream
  "Computes SRI hash for stream with given algorithm.
   Returns SRI string format: '{algo}-{base64-hash}'.

   Only accepts algorithms defined in the W3C SRI spec:
   sha256, sha384, sha512 (exact lowercase names).
   See: https://w3c.github.io/webappsec-subresource-integrity/"
  [algo ^java.io.InputStream in]
  (if-let [java-algo (get sri-algorithm-map algo)]
    (let [digest-bytes (.digest (hash-stream-data java-algo in))]
      (str algo "-" (codec/->base64 digest-bytes)))
    (throw (ex-info "Invalid SRI algorithm"
                    {:algorithm algo
                     :valid-algorithms (set (keys sri-algorithm-map))}))))

(defn sri-hash-file
  "Computes SRI hash of file for given algorithm.
   Only accepts W3C SRI spec algorithms: sha256, sha384, sha512."
  [algo file-path]
  (let [file (io/file file-path)]
    (when (.exists file)
      (with-open [in (io/input-stream file)]
        (sri-hash-stream algo in)))))

(defn sri-hash-resource
  "Computes SRI hash of classpath resource for given algorithm.
   Only accepts W3C SRI spec algorithms: sha256, sha384, sha512."
  [algo path]
  (if-let [resource (io/resource path)]
    (with-open [in (io/input-stream resource)]
      (sri-hash-stream algo in))
    (throw (ex-info "Cannot load resource from classpath" {:path path}))))

(defn sri-sha384-file
  "Computes SHA-384 SRI hash of file."
  [file-path]
  (sri-hash-file "sha384" file-path))

(defn sri-sha384-resource
  "Computes SHA-384 SRI hash of classpath resource."
  [path]
  (sri-hash-resource "sha384" path))
