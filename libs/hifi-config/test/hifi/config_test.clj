
(ns hifi.config-test
  (:require [hifi.config :as sut]
            [clojure.java.io :as io]
            [expectations.clojure.test :refer [defexpect expect more->]]
            [clojure.test :refer [deftest is testing]]))

(defexpect test-read-config-exception
  (testing "read-config throws exception when the config file doesn't exist"
    (expect (more-> clojure.lang.ExceptionInfo type
                    #"filename" ex-message
                    {:hifi/error :hifi.config/invalid-file :filename "not-exist.edn" :options {}} ex-data)
            (sut/-read-config "not-exist.edn" {})))
  (testing "read-config throws exception when the config filename is nil"
    (expect (more-> clojure.lang.ExceptionInfo type
                    #"Filename" ex-message
                    {:hifi/error :hifi.config/invalid-file :filename nil :options {}} ex-data)
            (sut/-read-config nil {}))))

(deftest test-config
  (testing "read-config works with aero"
    (let [c (sut/-read-config (io/resource "hifi/config/fixture.edn") {})]
      (is (= "world" (:hello c)))
      (is (= "bar" (:foo c)))
      (is (sut/secret? (:api-key c)))
      (is (not= "1234" (:api-key c)))
      (is (= "1234" (sut/unmask (:api-key c))))
      (System/setProperty "FOO" "baz")
      (let [c (sut/-read-config (io/resource "hifi/config/fixture.edn") {})]
        (is (= "baz" (:foo c))))
      (System/clearProperty "FOO"))))
