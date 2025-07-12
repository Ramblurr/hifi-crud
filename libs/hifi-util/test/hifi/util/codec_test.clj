;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2 

 (ns hifi.util.codec-test
   (:require
    [clojure.test :refer [deftest testing is are]]
    [hifi.util.codec :as codec]))

(deftest ->hex-test
  (testing "converts byte array to hex string"
    (are [input expected] (= expected (codec/->hex input))
      (.getBytes "test") "74657374"
      (.getBytes "hello") "68656c6c6f"
      (.getBytes "") ""
      (byte-array [0 15 16 255]) "000f10ff"
      (byte-array [127 -128 -1]) "7f80ff"))) ;; Testing signed bytes

(deftest ->base64-test
  (testing "converts byte array to base64 URL-safe string without padding"
    (are [input expected] (= expected (codec/->base64 input))
      (.getBytes "test") "dGVzdA"
      (.getBytes "hello") "aGVsbG8"
      (.getBytes "") ""
      ;; Test that it's URL-safe (uses - and _ instead of + and /)
      (byte-array [251 255]) "-_8")))

(deftest digest-test
  (testing "creates consistent digest based on Clojure hash"
    (let [digest1 (codec/digest "test")
          digest2 (codec/digest "test")]
      (is (= digest1 digest2) "Digest should be consistent"))

    (testing "different inputs produce different digests"
      (is (not= (codec/digest "test1") (codec/digest "test2"))))))