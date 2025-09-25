;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets.config-test
  (:require
   [babashka.fs :as fs]
   [hifi.assets.processors :as processors]
   [clojure.test :refer [deftest testing is are]]
   [hifi.assets.config :as config]))

(deftest load-config-test
  (testing "deep merges map config with defaults"
    (is (= #:hifi.assets{:excluded-paths []
                         :manifest-path  "/tmp/wow/target/resources/public/assets/manifest.edn"
                         :output-dir     "/tmp/wow/target/resources/public/assets"
                         :paths          ["custom"]
                         :prefix         "/assets"
                         :processors     processors/default-processors
                         :project-root   "/tmp/wow"
                         :verbose?       true}
           (config/load-config #:hifi.assets{:paths ["custom"] :project-root "/tmp/wow"}))))

  (testing "throws on invalid config type"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/load-config 123)))))

(deftest validate-config-test
  (testing "validates and coerces empty config"
    (is (= #:hifi.assets{:excluded-paths []
                         :paths          ["assets"]
                         :prefix         "/assets"
                         :processors     processors/default-processors
                         :manifest-path  (str (fs/path (fs/canonicalize ".") "target/resources/public/assets/manifest.edn"))
                         :project-root   (str (fs/canonicalize "."))
                         :output-dir     (str (fs/path (fs/canonicalize ".") "target/resources/public/assets"))
                         :verbose?       true}
           (config/-validate-config {}))))

  (testing "validates custom config"
    (is (= #:hifi.assets{:excluded-paths ["src/assets/raw"]
                         :manifest-path  (str (fs/path (fs/canonicalize ".") "target/resources/public/assets/manifest.edn"))
                         :project-root   (str (fs/canonicalize "."))
                         :output-dir     (str (fs/path (fs/canonicalize ".") "target/resources/public/assets"))
                         :prefix         "/assets"
                         :paths          ["src/assets" "vendor/assets"]
                         :processors     processors/default-processors
                         :verbose?       true}
           (config/-validate-config #:hifi.assets{:paths          ["src/assets" "vendor/assets"]
                                                  :excluded-paths ["src/assets/raw"]}))))
  (testing "throws on invalid paths type"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/-validate-config {:hifi.assets/paths "not-a-vector"})))))
