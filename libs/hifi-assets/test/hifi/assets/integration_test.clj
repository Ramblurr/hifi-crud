;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

(ns hifi.assets.integration-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [babashka.fs :as fs]
   [hifi.assets :as assets]
   [hifi.assets.scanner :as scanner]
   [hifi.assets.digest :as digest]
   [hifi.assets.manifest :as manifest]))

(defn with-temp-dir [f]
  (let [temp-dir (fs/create-temp-dir)]
    (try
      (f (str temp-dir))
      (finally
        (fs/delete-tree temp-dir)))))

(deftest scanner-digest-integration-test
  (testing "scanning and digesting real files"
    (with-temp-dir
      (fn [temp-dir]
        ;; Create asset structure
        (fs/create-dirs (str temp-dir "/assets/js"))
        (fs/create-dirs (str temp-dir "/assets/css"))

        ;; Create files with known content
        (spit (str temp-dir "/assets/js/app.js") "console.log('hello');")
        (spit (str temp-dir "/assets/css/main.css") "body { margin: 0; }")

        ;; Create a pre-digested file
        (spit (str temp-dir "/assets/js/vendor-abc12345.digested.js") "// vendor code")

        ;; Scan assets
        (let [scanned (scanner/scan-asset-paths [(str temp-dir "/assets")] [])
              digest-results (map (fn [asset]
                                    (digest/digest-file-content (:full-path asset)
                                                                (:logical-path asset)))
                                  scanned)]

          (testing "scanned correct number of files"
            (is (= 3 (count scanned))))

          (testing "digest results for regular files"
            (let [app-js-digest (first (filter #(= "js/app.js" (:logical-path %)) digest-results))]
              (is (= "app.js" (:original-name app-js-digest)))
              (is (string? (:sha256-hash app-js-digest)))
              (is (= 64 (count (:sha256-hash app-js-digest)))) ;; SHA256 = 64 hex chars
              (is (str/starts-with? (:sri-hash app-js-digest) "sha384-"))
              (is (false? (:pre-digested? app-js-digest)))
              (is (= 21 (:size app-js-digest))) ;; "console.log('hello');" = 21 bytes
              (is (str/starts-with? (:digest-name app-js-digest) "app-"))))

          (testing "digest results for pre-digested files"
            (let [vendor-digest (first (filter #(:pre-digested? %) digest-results))]
              (is (= "vendor.js" (:original-name vendor-digest)))
              (is (= "vendor-abc12345.digested.js" (:digest-name vendor-digest)))
              (is (= "abc12345" (:sha256-hash vendor-digest)))
              (is (true? (:pre-digested? vendor-digest)))
              (is (str/starts-with? (:sri-hash vendor-digest) "sha384-")))))))))

(deftest full-pipeline-test
  (testing "complete asset pipeline workflow"
    (with-temp-dir
      (fn [temp-dir]
        ;; Create assets
        (fs/create-dirs (str temp-dir "/assets/js"))
        (fs/create-dirs (str temp-dir "/assets/css"))
        (fs/create-dirs (str temp-dir "/assets/images"))

        ;; Create files
        (spit (str temp-dir "/assets/js/app.js") "// app code")
        (spit (str temp-dir "/assets/css/styles.css") "/* styles */")
        (spit (str temp-dir "/assets/images/logo.png") "fake png data")

        ;; Create output directory
        (fs/create-dirs (str temp-dir "/output"))

        ;; Scan and digest
        (let [config {:hifi/assets {:paths [(str temp-dir "/assets")]}
                      :excluded-paths []}
              scanned (scanner/scan-assets-from-config config)
              digest-infos (map (fn [asset]
                                  (digest/digest-file-content (:full-path asset)
                                                              (:logical-path asset)))
                                scanned)
              manifest-data (manifest/generate-manifest digest-infos)
              manifest-path (str temp-dir "/output/manifest.edn")]

          ;; Write manifest
          (manifest/write-manifest manifest-data manifest-path)

          (testing "manifest was created"
            (is (.exists (io/file manifest-path))))

          (testing "manifest contains all assets"
            (is (= 3 (count manifest-data)))
            (is (contains? manifest-data "js/app.js"))
            (is (contains? manifest-data "css/styles.css"))
            (is (contains? manifest-data "images/logo.png")))

          (testing "manifest entries have correct structure"
            (let [app-entry (get manifest-data "js/app.js")]
              (is (string? (:digest-path app-entry)))
              (is (str/starts-with? (:digest-path app-entry) "app-"))
              (is (str/ends-with? (:digest-path app-entry) ".js"))
              (is (str/starts-with? (:integrity app-entry) "sha384-"))
              (is (number? (:size app-entry)))
              (is (string? (:last-modified app-entry)))))

          (testing "manifest can be loaded back"
            (let [loaded-manifest (manifest/load-manifest manifest-path)]
              (is (= (count manifest-data) (count loaded-manifest)))
              (is (= (get-in manifest-data ["js/app.js" :digest-path])
                     (get-in loaded-manifest ["js/app.js" :digest-path]))))))))))

(deftest asset-exists-check-test
  (testing "checking if assets exist in development mode"
    (with-temp-dir
      (fn [temp-dir]
        (fs/create-dirs (str temp-dir "/assets/js"))
        (spit (str temp-dir "/assets/js/app.js") "// code")

        (let [config {:hifi/assets {:paths [(str temp-dir "/assets")]}}
              asset-ctx (assets/create-asset-context {:dev-mode? true
                                                      :config config})]

          (testing "returns true for existing asset"
            (is (assets/asset-exists? asset-ctx "js/app.js")))

          (testing "returns false for non-existing asset"
            (is (not (assets/asset-exists? asset-ctx "js/missing.js")))))))))