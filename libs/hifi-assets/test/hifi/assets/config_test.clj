;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2 

 (ns hifi.assets.config-test
   (:require
    [clojure.test :refer [deftest testing is are]]
    [hifi.assets.config :as config]))

(deftest load-config-test
  (testing "returns default config when nil"
    (let [result (config/load-config nil)]
      (is (= config/default-config result))))

  (testing "deep merges map config with defaults"
    (let [result (config/load-config {:hifi/assets {:paths ["custom"]}})]
      (is (= ["custom"] (get-in result [:hifi/assets :paths])))
      ;; With deep-merge, other default values are preserved
      (is (= [] (get-in result [:hifi/assets :excluded-paths])))
      (is (= "target/resources/public/assets" (get-in result [:hifi/assets :output-path])))))

  (testing "throws on invalid config type"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/load-config 123)))))

(deftest validate-config-test
  (testing "validates and coerces empty config"
    (let [result (config/validate-config {})]
      (is (= ["assets"] (:paths result)))
      (is (= [] (:excluded-paths result)))
      (is (= "target/resources/public/assets" (:output-path result)))
      (is (= "target/resources/public/assets/manifest.edn" (:manifest-path result)))
      (is (= "" (:base-url result)))))

  (testing "validates custom config"
    (let [result (config/validate-config {:hifi/assets {:paths ["src/assets" "vendor/assets"]
                                                        :excluded-paths ["src/assets/raw"]
                                                        :base-url "/static"}})]
      (is (= ["src/assets" "vendor/assets"] (:paths result)))
      (is (= ["src/assets/raw"] (:excluded-paths result)))
      (is (= "/static" (:base-url result)))))

  (testing "throws on invalid paths type"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/validate-config {:hifi/assets {:paths "not-a-vector"}})))))

(deftest helper-functions-test
  (let [config {:hifi/assets {:paths ["custom/path"]
                              :excluded-paths ["excluded"]
                              :output-path "custom/output"
                              :manifest-path "custom/manifest.edn"}}]
    (testing "get-asset-paths"
      (is (= ["custom/path"] (config/get-asset-paths config))))

    (testing "get-excluded-paths"
      (is (= ["excluded"] (config/get-excluded-paths config))))

    (testing "get-output-path"
      (is (= "custom/output" (config/get-output-path config))))

    (testing "get-manifest-path"
      (is (= "custom/manifest.edn" (config/get-manifest-path config))))))