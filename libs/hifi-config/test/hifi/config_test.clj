(ns hifi.config-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [hifi.config :as sut]))

(def test-key-file-path (-> "hifi/config/keys.txt" (io/resource) (io/file) .getAbsolutePath))
(def fixture (io/resource "hifi/config/fixture.edn"))

(deftest test-read-config-exception
  (testing "read-config throws exception when the config file doesn't exist"
    (let [e (try
              (sut/read-config "not-exist.edn" {})
              (catch Exception e e))]
      (is (instance? clojure.lang.ExceptionInfo e))
      (is (re-find #"source" (ex-message e)))
      (is (= :hifi.config/invalid-file (:hifi/error (ex-data e))))
      (is (= "not-exist.edn" (:source (ex-data e))))))
  (testing "read-config throws exception when the config filename is nil"
    (let [e (try
              (sut/read-config nil {})
              (catch Exception e e))]
      (is (instance? clojure.lang.ExceptionInfo e))
      (is (re-find #"Filename" (ex-message e)))
      (is (= :hifi.config/invalid-file (:hifi/error (ex-data e))))
      (is (nil? (:filename (ex-data e)))))))

(deftest test-config
  (testing "read-config works with aero"
    (let [opts {:hifi/sops {:env {"SOPS_AGE_KEY_FILE" test-key-file-path}}}
          c    (sut/read-config fixture
                                opts)]
      (is (sut/secret? (:api-key c)))
      (is (sut/secret? (get-in c [:secrets :password])))
      (is (sut/secret? (get-in c [:secrets :database :mysql :credential])))
      (is (sut/secret? (get-in c [:secrets :email :senders 0 :key])))
      (is (=
           {:api-key "1234"
            :foo     "bar"
            :hello   "world"
            :profile nil
            :secrets {:database {:mysql {:credential "foobar"}}
                      :email    {:senders [{:id  :sender1
                                            :key "hunter2"}]}
                      :password "hunter2"}}
           (sut/unmask c)))
      (System/setProperty "FOO" "baz")
      (is (= "baz" (-> fixture  (sut/read-config opts) :foo)))
      (System/clearProperty "FOO"))))

(deftest test-mask-deep
  (testing "mask-deep recursively masks all leaf values"
    (let [data {:name "Alice"
                :age 30
                :config {:api-key "secret123"
                         :endpoint "https://api.example.com"}
                :tags ["tag1" "tag2"]}
          masked (sut/mask-deep data)]
      (is (sut/secret? (:name masked)))
      (is (sut/secret? (:age masked)))
      (is (sut/secret? (get-in masked [:config :api-key])))
      (is (sut/secret? (get-in masked [:config :endpoint])))
      (is (= "Alice" (sut/unmask (:name masked))))
      (is (= 30 (sut/unmask (:age masked))))
      (is (= "secret123" (sut/unmask (get-in masked [:config :api-key]))))
      (is (= "https://api.example.com" (sut/unmask (get-in masked [:config :endpoint]))))))
  (testing "mask-deep preserves structure"
    (let [data {:outer {:inner {:value 42}}}
          masked (sut/mask-deep data)]
      (is (map? masked))
      (is (map? (:outer masked)))
      (is (map? (get-in masked [:outer :inner])))
      (is (= 42 (sut/unmask (get-in masked [:outer :inner :value]))))))
  (testing "mask-deep doesn't double-mask already masked values"
    (let [secret (sut/mask "already-secret")
          data {:key secret}
          masked (sut/mask-deep data)]
      (is (identical? secret (:key masked))))))
