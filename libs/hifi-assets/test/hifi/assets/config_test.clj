;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
";; SPDX-License-Identifier: EUPL-1.2 "

(ns hifi.assets.config-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest testing is are]]
   [hifi.assets.config :as config]))

(deftest load-config-test
  (testing "deep merges map config with defaults"
    (let [result (config/load-config #:hifi.assets{:paths ["custom"] :project-root "/tmp/wow"})]
      (is (= #:hifi.assets{:excluded-paths []
                           :manifest-path "target/resources/public/assets/manifest.edn"
                           :output-dir "target/resources/public/assets"
                           :paths ["custom"]
                           :prefix "/assets"
                           :project-root (fs/canonicalize "/tmp/wow")}
             (dissoc result :hifi.assets/processors)))
      (is (= 2 (count (:hifi.assets/processors result))))
      (is (= #{"text/css"} (:mime-types (first (:hifi.assets/processors result)))))
      (is (= #{"application/javascript"} (:mime-types (second (:hifi.assets/processors result)))))))

  (testing "throws on invalid config type"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/load-config 123)))))

(deftest validate-config-test
  (testing "validates and coerces empty config"
    (let [result (config/-validate-config {})]
      (is (= #:hifi.assets{:excluded-paths []
                           :manifest-path "target/resources/public/assets/manifest.edn"
                           :output-dir "target/resources/public/assets"
                           :paths ["assets"]
                           :prefix "/assets"
                           :project-root (fs/canonicalize ".")}
             (dissoc result :hifi.assets/processors)))
      (is (= 2 (count (:hifi.assets/processors result))))
      (is (= #{"text/css"} (:mime-types (first (:hifi.assets/processors result)))))
      (is (= #{"application/javascript"} (:mime-types (second (:hifi.assets/processors result)))))))

  (testing "validates custom config"
    (let [result (config/-validate-config #:hifi.assets{:paths ["src/assets" "vendor/assets"]
                                                        :excluded-paths ["src/assets/raw"]})]
      (is (= #:hifi.assets{:excluded-paths ["src/assets/raw"]
                           :manifest-path "target/resources/public/assets/manifest.edn"
                           :project-root (fs/canonicalize ".")
                           :output-dir "target/resources/public/assets"
                           :prefix "/assets"
                           :paths ["src/assets" "vendor/assets"]}
             (dissoc result :hifi.assets/processors)))
      (is (= 2 (count (:hifi.assets/processors result))))))

  (testing "throws on invalid paths type"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/-validate-config {:hifi.assets/paths "not-a-vector"})))))
