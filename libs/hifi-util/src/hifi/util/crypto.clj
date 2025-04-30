;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.util.crypto
  "Utilities related to security"
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
  "Returns a random byte array of the specified size."
  ^byte/1  [size]
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
    (let [d      (MessageDigest/getInstance algo)
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

(defn sha384-resource [path]
  (if-let [resource (io/resource path)]
    (str "sha384-"
         (-> resource
             io/input-stream
             sha384-stream
             codec/->base64))
    (throw (ex-info "Cannot load resource %s from classpath" {:path path}))))

