;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
";; SPDX-License-Identifier: EUPL-1.2 "

(ns hifi.assets.config-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest testing is are]]
   [hifi.assets.config :as config]))

(deftest load-config-test
  (testing "deep merges map config with defaults"
    (is (= #:hifi.assets{:excluded-paths []
                         :manifest-path "target/resources/public/assets/manifest.edn"
                         :output-dir "target/resources/public/assets"
                         :paths ["custom"]
                         :project-root (fs/canonicalize "/tmp/wow")}
           (config/load-config #:hifi.assets{:paths ["custom"] :project-root "/tmp/wow"}))))

  (testing "throws on invalid config type"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/load-config 123)))))

(deftest validate-config-test
  (testing "validates and coerces empty config"
    (is (= #:hifi.assets{:excluded-paths []
                         :manifest-path "target/resources/public/assets/manifest.edn"
                         :output-dir "target/resources/public/assets"
                         :paths ["assets"]
                         :project-root (fs/canonicalize ".")}
           (config/-validate-config {}))))

  (testing "validates custom config"
    (is (= #:hifi.assets{:excluded-paths ["src/assets/raw"]
                         :manifest-path "target/resources/public/assets/manifest.edn"
                         :project-root (fs/canonicalize ".")
                         :output-dir "target/resources/public/assets"
                         :paths ["src/assets" "vendor/assets"]}
           (config/-validate-config #:hifi.assets{:paths ["src/assets" "vendor/assets"]
                                                  :excluded-paths ["src/assets/raw"]}))))

  (testing "throws on invalid paths type"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/-validate-config {:hifi.assets/paths "not-a-vector"})))))
