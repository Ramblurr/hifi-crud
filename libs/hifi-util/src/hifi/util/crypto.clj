;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.util.crypto
  "Utilities related to security"
  (:refer-clojure :exclude [bytes])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [crypto.equality :as equality]
   [hifi.util.codec :as codec])
  (:import
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]
   [java.security SecureRandom MessageDigest]))

(def ^SecureRandom secure-random
  (SecureRandom/new))

(defn bytes
  "Returns a random byte array of the specified size."
  ^byte/1 [size]
  (let [seed (byte-array size)]
    (.nextBytes secure-random seed)
    seed))

(defn rand-string [n]
  (codec/->base64 (bytes n)))

(defn random-unguessable-uid
  "Returns a URL-safe base64-encoded 160-bit (20 byte) random value.

  Speed is similar random-uuid. See https://neilmadden.blog/2018/08/30/moving-away-from-uuids/
  for an article (from 2018!) on why UUIDs should not be used in cases where cryptographically secure
  randomness is required."
  []
  (codec/->base64 (bytes 20)))

(def new-uid
  "Allows uid implementation to be changed if need be."
  random-unguessable-uid)

(def eq?
  "Test whether two sequences of characters or bytes are equal in a way that
  protects against timing attacks. Note that this does not prevent an attacker
  from discovering the *length* of the data being compared."
  equality/eq?)

(defn secret-key->hmac-sha256-keyspec [secret-key]
  (SecretKeySpec/new (String/.getBytes secret-key) "HmacSHA256"))

(defn hmac-sha256
  "Used for quick stateless csrf token generation."
  [key-spec data]
  (-> (doto (Mac/getInstance "HmacSHA256")
        (.init key-spec))
      (.doFinal (String/.getBytes data))
      codec/->base64))

(defn hash-stream-data ^MessageDigest
  [^String algo ^java.io.InputStream in]
  (when in
    (let [d (MessageDigest/getInstance algo)
          buffer (byte-array 5120)]
      (loop []
        (let [read-size (.read in buffer 0 5120)]
          (when-not (= read-size -1)
            (.update d ^bytes buffer 0 read-size)
            (recur))))
      d)))

(defn sha384
  ^bytes [^bytes input-bytes]
  (-> (doto (MessageDigest/getInstance "SHA-384")
        (.update input-bytes))
      (.digest)))

(defn sha384-stream
  ^bytes [^bytes ^java.io.InputStream in]
  (.digest (hash-stream-data "SHA-384" in)))

(defn sha384-resource
  "Computes SHA-384 hash of a classpath resource and returns bytes."
  ^bytes [path]
  (if-let [resource (io/resource path)]
    (with-open [in (io/input-stream resource)]
      (sha384-stream in))
    (throw (ex-info "Cannot load resource from classpath" {:path path}))))

(defn sha256
  "Computes SHA-256 hash of byte array."
  ^bytes [^bytes input-bytes]
  (-> (doto (MessageDigest/getInstance "SHA-256")
        (.update input-bytes))
      (.digest)))

(defn sha256-stream
  "Computes SHA-256 hash of input stream."
  ^bytes [^java.io.InputStream in]
  (.digest (hash-stream-data "SHA-256" in)))

(defn sha256-file
  "Computes SHA-256 hash of a file and returns bytes."
  ^bytes [file-path]
  (let [file (io/file file-path)]
    (when (.exists file)
      (with-open [in (io/input-stream file)]
        (sha256-stream in)))))

(defn sha256-file-hex
  "Computes SHA-256 hash of a file and returns hex string."
  [file-path]
  (when-let [hash-bytes (sha256-file file-path)]
    (codec/->hex hash-bytes)))

(defn sha384-file
  "Computes SHA-384 hash of a file and returns bytes."
  ^bytes [file-path]
  (let [file (io/file file-path)]
    (when (.exists file)
      (with-open [in (io/input-stream file)]
        (sha384-stream in)))))

(defn sha256-resource
  "Computes SHA-256 hash of a classpath resource and returns bytes."
  ^bytes [path]
  (if-let [resource (io/resource path)]
    (with-open [in (io/input-stream resource)]
      (sha256-stream in))
    (throw (ex-info "Cannot load resource from classpath" {:path path}))))

(defn sha256-resource-hex
  "Computes SHA-256 hash of a classpath resource and returns hex string."
  [path]
  (codec/->hex (sha256-resource path)))

(defn sri-hash-stream
  "Computes SRI hash for a stream with the given algorithm.
   Returns the full SRI string (e.g., 'sha384-...')."
  [algo ^java.io.InputStream in]
  (let [digest-bytes (.digest (hash-stream-data algo in))
        ;; Convert algorithm name to SRI format (e.g., "SHA-384" -> "sha384")
        sri-algo (-> algo
                     str/lower-case
                     (str/replace "-" ""))]
    (str sri-algo "-" (codec/->base64 digest-bytes))))

(defn sri-hash-file
  "Computes SRI hash of a file for the given algorithm."
  [algo file-path]
  (let [file (io/file file-path)]
    (when (.exists file)
      (with-open [in (io/input-stream file)]
        (sri-hash-stream algo in)))))

(defn sri-hash-resource
  "Computes SRI hash of a classpath resource for the given algorithm."
  [algo path]
  (if-let [resource (io/resource path)]
    (with-open [in (io/input-stream resource)]
      (sri-hash-stream algo in))
    (throw (ex-info "Cannot load resource from classpath" {:path path}))))

(defn sri-sha384-file
  "Computes SHA-384 hash of a file and returns base64 string for SRI."
  [file-path]
  (sri-hash-file "SHA-384" file-path))

(defn sri-sha384-resource
  "Computes SHA-384 hash of a classpath resource and returns SRI string."
  [path]
  (sri-hash-resource "SHA-384" path))

