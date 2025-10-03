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
   [clojure.string :as str]
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [crypto.equality :as equality]
   [hifi.util.codec :as codec]
   [hifi.util.random :as random])
  (:import
   [java.security MessageDigest]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]))

(def ^:dynamic *secure-random*
  "This is a zero arity function that returns a [[java.security.SecureRandom]] suitable for cryptographic purposes."
  random/secure-random)

(defn secure-random
  "See [[*secure-random]]"
  ^java.security.SecureRandom [] (*secure-random*))

(defn bytes
  "Returns secure random bytes of specified size."
  ^bytes [size]
  (let [buf (byte-array size)]
    (.nextBytes (secure-random) buf)
    buf))

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
  (let [file (fs/file file-path)]
    (when (fs/exists? file)
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
  (let [file (fs/file file-path)]
    (when (fs/exists? file)
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

(defn ^:private hmac-sha256-bytes
  "Low-level HMAC-SHA256 (bytes in → bytes out).
   Key and data are byte arrays."
  ^bytes [^SecretKeySpec key ^bytes data]
  (let [mac (doto (Mac/getInstance "HmacSHA256")
              (.init (SecretKeySpec. key "HmacSHA256")))]
    (.doFinal mac data)))

(defn ^:private hkdf-sha256-extract
  "HKDF-Extract(step) per RFC 5869 with SHA-256.
   If salt is nil or empty, uses a HashLen(=32) zero key."
  ^bytes [^bytes salt ^bytes ikm]
  (let [zero-salt (byte-array 32)]
    (hmac-sha256-bytes (if (or (nil? salt) (zero? (alength ^bytes salt)))
                         zero-salt
                         salt)
                       ikm)))

(defn ^:private hkdf-sha256-expand
  "HKDF-Expand(step) per RFC 5869 with SHA-256.
   prk: output from hkdf-sha256-extract (32 bytes)
   info: context/application-specific data (may be nil/empty)
   length: length of output keying material in bytes."
  ^bytes [^bytes prk ^bytes info length]
  (let [info     (or info (byte-array 0))
        blocks   (long (Math/ceil (/ (double length) 32.0)))
        out      (byte-array length)]
    (loop [t (byte-array 0)
           i 1
           offset 0]
      (if (<= i blocks)
        (let [buf (byte-array (+ (alength t) (alength info) 1))
              _   (System/arraycopy t 0 buf 0 (alength t))
              _   (System/arraycopy info 0 buf (alength t) (alength info))
              _   (aset-byte buf (dec (alength buf)) (byte i))
              t'  (hmac-sha256-bytes prk buf)
              n   (min 32 (- length offset))]
          (System/arraycopy t' 0 out offset n)
          (recur t' (inc i) (+ offset n)))
        out))))

(defn derive-subkey-hkdf-sha256
  "Derives a context-bound subkey from input keying material using HKDF-SHA256.

   Parameters:
   - ikm: input keying material (byte array), e.g. your primary (master) key bytes
   - opts (map):
     :salt  (byte array, optional) non-secret randomization; if nil, HKDF uses zero-salt
     :info  (byte array, optional) context string (binds the subkey to a purpose, must be unique per context)
     :length (int, optional) desired output length in bytes (default 32)

   Warning: ikm must be random bytes, and not a password. Do not put  passwords
   or passphrases anywhere near here.

   Returns: derived key as byte-array of length `length`."
  ^bytes [^bytes ikm & [{:keys [^bytes salt ^bytes info length]
                         :or   {length 32}}]]
  (let [prk (hkdf-sha256-extract salt ikm)]
    (hkdf-sha256-expand prk info length)))

(defn secret-bytes->hmac-sha256-keyspec
  "Converts raw secret key bytes into an HMAC-SHA256 SecretKeySpec."
  [^bytes secret-bytes]
  (SecretKeySpec. secret-bytes "HmacSHA256"))

(defn key->hmac-sha256-keyspec
  "Converts a hex encoded secret key into an HMAC-SHA256 SecretKeySpec."
  [^String hex]
  (SecretKeySpec. (codec/hex-> hex) "HmacSHA256"))

(defn derive-hmac-keyspec
  "Derives an HMAC-SHA256 keyspec from a primary key using HKDF.

   Takes a primary `SecretKeySpec` and derives a context-bound subkey using
   the provided `info` string as the HKDF context parameter.

   Parameters:
   - primary-key: A `SecretKeySpec` containing the primary (master) key bytes
   - info: A non-empty string used as the HKDF context parameter. This binds 
           the derived key to a specific purpose and must be unique per context.
           Must not be blank.
   - opts (map, optional):
     :salt   - Non-secret randomization bytes (optional)
     :length - Desired output length in bytes (default: 32)

   Returns: A new `SecretKeySpec` suitable for HMAC-SHA256 operations.

   Throws: `ex-info` if `info` is blank or nil.

   Example:
   ```clojure
   (derive-hmac-keyspec primary-key \"my-app.csrf/v1\" {:length 32})
   ```"
  [^SecretKeySpec primary-key info & [{:keys [^bytes salt ^bytes length]
                                       :or   {length 32}}]]
  (when (str/blank? info)
    (throw (ex-info "info parameter cannot be blank - it must be a non-empty string that uniquely identifies the key's purpose"
                    {:info info})))
  (-> primary-key
      (derive-subkey-hkdf-sha256 {:salt salt :info (.getBytes info) :length length})
      (secret-bytes->hmac-sha256-keyspec)))

(defn derive-csrf-hmac-keyspec
  "Derives an HMAC-SHA256 key for CSRF double-submit from the primary key.

   See [[derive-hmac-keyspec]] for the options.

   Returns: javax.crypto.spec.SecretKeySpec suitable for HMAC-SHA256."
  [^SecretKeySpec primary-key & opts]
  (derive-hmac-keyspec primary-key "hifi.csrf.hmac/v1" opts))
