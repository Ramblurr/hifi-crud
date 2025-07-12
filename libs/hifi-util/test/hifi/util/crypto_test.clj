;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

(ns hifi.util.crypto-test
  (:require
   [clojure.test :refer [deftest testing is are]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hifi.util.crypto :as crypto]
   [hifi.util.codec :as codec]))

(deftest hash-bytes-test
  (testing "hash functions compute correct values"
    (are [hash-fn expected-hex input]
         (= expected-hex (codec/->hex (hash-fn (.getBytes input))))
      ;; Test vectors from https://www.di-mgt.com.au/sha_testvectors.html
      crypto/sha256 "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" "abc"
      crypto/sha256 "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" ""
      crypto/sha384 "cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed8086072ba1e7cc2358baeca134c825a7" "abc")))

(deftest hash-file-test
  (testing "file hash functions"
    (let [temp-file (java.io.File/createTempFile "test" ".txt")
          test-content "hello world"
          sha256-expected "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"]
      (try
        (spit temp-file test-content)
        (testing "compute correct hash values"
          (are [hash-fn byte-count] (let [result (hash-fn (.getAbsolutePath temp-file))]
                                      (and (bytes? result)
                                           (= byte-count (count result))))
            crypto/sha256-file 32
            crypto/sha384-file 48)

          (is (= sha256-expected (crypto/sha256-file-hex (.getAbsolutePath temp-file)))))

        (finally
          (.delete temp-file))))

    (testing "return nil for non-existent files"
      (are [hash-fn] (nil? (hash-fn "/non/existent/file.txt"))
        crypto/sha256-file
        crypto/sha256-file-hex
        crypto/sha384-file))))

(deftest hash-resource-test
  (testing "resource hash functions"
    (let [test-resource "hifi/util/crypto_test.clj"]
      (testing "compute hashes for existing resources"
        (are [hash-fn byte-count] (let [result (hash-fn test-resource)]
                                    (and (bytes? result)
                                         (= byte-count (count result))))
          crypto/sha256-resource 32
          crypto/sha384-resource 48)

        (let [hex-hash (crypto/sha256-resource-hex test-resource)]
          (is (string? hex-hash))
          (is (= 64 (count hex-hash)))
          (is (re-matches #"[0-9a-f]{64}" hex-hash)))))

    (testing "throw for non-existent resources"
      (are [hash-fn] (thrown? clojure.lang.ExceptionInfo
                              (hash-fn "non/existent/resource.txt"))
        crypto/sha256-resource
        crypto/sha256-resource-hex
        crypto/sha384-resource))))

(deftest sri-hash-test
  (testing "SRI hash generation"
    (testing "generates correct format"
      (let [test-bytes (.getBytes "test")]
        (are [algo expected-prefix]
             (let [sri (crypto/sri-hash-stream algo (io/input-stream test-bytes))]
               (and (str/starts-with? sri expected-prefix)
                    (re-matches (re-pattern (str expected-prefix "[A-Za-z0-9+/_-]+")) sri)))
          "sha256" "sha256-"
          "sha384" "sha384-"
          "sha512" "sha512-")))

    (testing "validates algorithm"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Invalid SRI algorithm"
                            (crypto/sri-hash-stream "MD5" (io/input-stream (.getBytes "test"))))))

    (testing "works with files"
      (let [temp-file (java.io.File/createTempFile "test" ".txt")]
        (try
          (spit temp-file "test content")
          (are [algo expected-prefix]
               (str/starts-with? (crypto/sri-hash-file algo (.getAbsolutePath temp-file))
                                 expected-prefix)
            "sha256" "sha256-"
            "sha384" "sha384-"
            "sha512" "sha512-")

          (let [sri (crypto/sri-sha384-file (.getAbsolutePath temp-file))]
            (is (str/starts-with? sri "sha384-"))
            (is (re-matches #"sha384-[A-Za-z0-9+/=_-]+" sri)))

          (finally
            (.delete temp-file)))))

    (testing "works with resources"
      (let [sri (crypto/sri-sha384-resource "hifi/util/crypto_test.clj")]
        (is (str/starts-with? sri "sha384-"))
        (is (re-matches #"sha384-[A-Za-z0-9+/=_-]+" sri))))))

(deftest hash-consistency-test
  (testing "file and stream functions produce same results"
    (let [temp-file (java.io.File/createTempFile "test" ".txt")
          content "consistency test content"]
      (try
        (spit temp-file content)
        (let [file-hash (crypto/sha256-file-hex (.getAbsolutePath temp-file))
              stream-hash (with-open [in (io/input-stream temp-file)]
                            (codec/->hex (crypto/sha256-stream in)))]
          (is (= file-hash stream-hash)))
        (finally
          (.delete temp-file))))))

(deftest random-generation-test
  (testing "random value generation"
    (testing "bytes function"
      (are [size] (let [result (crypto/bytes size)]
                    (and (bytes? result)
                         (= size (count result))))
        16 20 32))))

(deftest hmac-test
  (testing "HMAC-SHA256"
    (let [key-spec (crypto/secret-key->hmac-sha256-keyspec "secret-key")
          data     "test data"
          hmac     (crypto/hmac-sha256 key-spec data)]
      (is (string? hmac))
      (is (re-matches #"[A-Za-z0-9+/=_-]+" hmac)))))

(deftest timing-safe-equality-test
  (testing "eq? function"
    (is (crypto/eq? "is this thing on?" "is this thing on?"))
    (is (not (crypto/eq? "a" "b")))))
