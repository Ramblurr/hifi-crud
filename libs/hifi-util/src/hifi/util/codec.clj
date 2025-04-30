;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.util.codec
  "Utilities relating to bytes and encodings, not security."
  (:import
   [java.util Base64 Base64$Encoder]))

(def ^Base64$Encoder base64-encoder
  (.withoutPadding (Base64/getUrlEncoder)))

(defn ->base64
  ^String [^byte/1 b]
  (.encodeToString base64-encoder b))

(defn digest
  "Digest function based on Clojure's hash."
  ^String [data]
  (->base64 (.getBytes (str (hash data)))))
