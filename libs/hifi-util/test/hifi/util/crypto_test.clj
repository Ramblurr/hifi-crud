;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2 

 (ns hifi.util.crypto-test
   (:require
    [clojure.test :refer [deftest testing is are]]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [hifi.util.crypto :as crypto]
    [hifi.util.codec :as codec]))

(deftest sha256-test
  (testing "computes SHA-256 hash correctly"
    ;; Test vector from https://www.di-mgt.com.au/sha_testvectors.html
    (let [hash-bytes (crypto/sha256 (.getBytes "abc"))
          hash-hex (codec/->hex hash-bytes)]
      (is (= "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" hash-hex))))

  (testing "handles empty input"
    (let [hash-bytes (crypto/sha256 (.getBytes ""))
          hash-hex (codec/->hex hash-bytes)]
      (is (= "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" hash-hex)))))

(deftest sha384-test
  (testing "computes SHA-384 hash correctly"
    ;; Test vector
    (let [hash-bytes (crypto/sha384 (.getBytes "abc"))
          hash-hex (codec/->hex hash-bytes)]
      (is (= "cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed8086072ba1e7cc2358baeca134c825a7" hash-hex)))))

(deftest sha256-file-test
  (testing "computes SHA-256 hash of a file returning bytes"
    (let [temp-file (java.io.File/createTempFile "test" ".txt")]
      (try
        (spit temp-file "hello world")
        (let [hash-bytes (crypto/sha256-file (.getAbsolutePath temp-file))]
          (is (bytes? hash-bytes))
          (is (= 32 (count hash-bytes))) ;; SHA-256 is 32 bytes
          (is (= "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
                 (codec/->hex hash-bytes))))
        (finally
          (.delete temp-file)))))

  (testing "returns nil for non-existent file"
    (is (nil? (crypto/sha256-file "/non/existent/file.txt")))))

(deftest sha256-file-hex-test
  (testing "computes SHA-256 hash of a file returning hex string"
    (let [temp-file (java.io.File/createTempFile "test" ".txt")]
      (try
        (spit temp-file "hello world")
        (is (= "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
               (crypto/sha256-file-hex (.getAbsolutePath temp-file))))
        (finally
          (.delete temp-file)))))

  (testing "returns nil for non-existent file"
    (is (nil? (crypto/sha256-file-hex "/non/existent/file.txt")))))

(deftest sha384-file-test
  (testing "computes SHA-384 hash of a file returning bytes"
    (let [temp-file (java.io.File/createTempFile "test" ".txt")]
      (try
        (spit temp-file "hello world")
        (let [hash-bytes (crypto/sha384-file (.getAbsolutePath temp-file))]
          (is (bytes? hash-bytes))
          (is (= 48 (count hash-bytes)))) ;; SHA-384 is 48 bytes
        (finally
          (.delete temp-file)))))

  (testing "returns nil for non-existent file"
    (is (nil? (crypto/sha384-file "/non/existent/file.txt")))))

(deftest sri-sha384-file-test
  (testing "computes SHA-384 SRI hash of a file"
    (let [temp-file (java.io.File/createTempFile "test" ".txt")]
      (try
        (spit temp-file "hello world")
        (let [sri-hash (crypto/sri-sha384-file (.getAbsolutePath temp-file))]
          (is (str/starts-with? sri-hash "sha384-"))
          ;; The base64 part should be valid
          (is (re-matches #"sha384-[A-Za-z0-9+/=_-]+" sri-hash)))
        (finally
          (.delete temp-file))))))

(deftest sha256-resource-test
  (testing "computes SHA-256 hash of a classpath resource returning bytes"
    ;; We need a known resource in the classpath - let's use this test file itself
    (let [hash-bytes (crypto/sha256-resource "hifi/util/crypto_test.clj")]
      (is (bytes? hash-bytes))
      (is (= 32 (count hash-bytes))))) ;; SHA-256 is 32 bytes

  (testing "throws for non-existent resource"
    (is (thrown? clojure.lang.ExceptionInfo
                 (crypto/sha256-resource "non/existent/resource.txt")))))

(deftest sha256-resource-hex-test
  (testing "computes SHA-256 hash of a classpath resource returning hex string"
    ;; We need a known resource in the classpath - let's use this test file itself
    (let [hash (crypto/sha256-resource-hex "hifi/util/crypto_test.clj")]
      (is (string? hash))
      (is (= 64 (count hash))) ;; SHA-256 is 32 bytes = 64 hex chars
      (is (re-matches #"[0-9a-f]{64}" hash))))

  (testing "throws for non-existent resource"
    (is (thrown? clojure.lang.ExceptionInfo
                 (crypto/sha256-resource-hex "non/existent/resource.txt")))))

(deftest sri-hash-stream-test
  (testing "generates correct SRI hash format"
    (let [input-stream (io/input-stream (.getBytes "test"))]
      (let [sri (crypto/sri-hash-stream "SHA-256" input-stream)]
        (is (str/starts-with? sri "sha256-"))
        (is (re-matches #"sha256-[A-Za-z0-9+/_-]+" sri))))))

(deftest sri-hash-file-test
  (testing "generates SRI hash for different algorithms"
    (let [temp-file (java.io.File/createTempFile "test" ".txt")]
      (try
        (spit temp-file "test content")
        (testing "SHA-256"
          (let [sri (crypto/sri-hash-file "SHA-256" (.getAbsolutePath temp-file))]
            (is (str/starts-with? sri "sha256-"))))
        (testing "SHA-384"
          (let [sri (crypto/sri-hash-file "SHA-384" (.getAbsolutePath temp-file))]
            (is (str/starts-with? sri "sha384-"))))
        (testing "SHA-512"
          (let [sri (crypto/sri-hash-file "SHA-512" (.getAbsolutePath temp-file))]
            (is (str/starts-with? sri "sha512-"))))
        (finally
          (.delete temp-file))))))

(deftest hash-consistency-test
  (testing "file and stream functions produce same results"
    (let [temp-file (java.io.File/createTempFile "test" ".txt")
          content "consistency test content"]
      (try
        (spit temp-file content)
        (let [file-sha256 (crypto/sha256-file-hex (.getAbsolutePath temp-file))
              stream-sha256 (with-open [in (io/input-stream temp-file)]
                              (codec/->hex (crypto/sha256-stream in)))]
          (is (= file-sha256 stream-sha256)))
        (finally
          (.delete temp-file))))))