;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.util.codec
  "Utilities relating to bytes and encodings, not security."
  (:import
   [java.util Base64 Base64$Encoder]))

(def ^Base64$Encoder base64-encoder
  (.withoutPadding (Base64/getUrlEncoder)))

(defn ->base64
  ^String [^bytes b]
  (.encodeToString base64-encoder b))

(defn digest
  "Digest function based on Clojure's hash."
  ^String [data]
  (->base64 (.getBytes (str (hash data)))))

(defn ->hex
  "Converts byte array to hex string using bit manipulation."
  ^String [^bytes b]
  (apply str (map #(format "%02x" (Byte/toUnsignedInt %)) b)))

(defn hex->
  "Convertes a hex string to a byte array."
  ^bytes [^String hex]
  (when (odd? (count hex))
    (throw (Exception. "Invalid hex string")))
  (byte-array
   (map #(unchecked-byte (Integer/parseInt % 16))
        (map #(apply str %)
             (partition 2 hex)))))
