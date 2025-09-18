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
        (let [scanned        (scanner/scan-assets [(str temp-dir "/assets")] {:hifi.assets/excluded-paths [] :hifi.assets/project-root temp-dir})
              digest-results (map (fn [asset]
                                    (digest/digest-file-content (:abs-path asset)
                                                                (:logical-path asset)))
                                  scanned)]

          (testing "scanned correct number of files"
            (is (= 3 (count scanned))))

          (testing "digest results for regular files"
            (let [app-js-digest (first (filter #(= "js/app.js" (:logical-path %)) digest-results))]
              (is (= {:original-name "app.js"
                      :pre-digested? false
                      :size          21}
                     (select-keys app-js-digest [:original-name :pre-digested? :size])))
              (is (= 64 (count (:sha256-hash app-js-digest))))
              (is (str/starts-with? (:sri-hash app-js-digest) "sha384-"))
              (is (str/starts-with? (:digest-name app-js-digest) "app-"))))

          (testing "digest results for pre-digested files"
            (let [vendor-digest (first (filter #(:pre-digested? %) digest-results))]
              (is (= {:original-name "vendor.js"
                      :digest-name   "vendor-abc12345.digested.js"
                      :sha256-hash   "abc12345"
                      :pre-digested? true}
                     (select-keys vendor-digest [:original-name :digest-name :sha256-hash :pre-digested?])))
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
        (let [config        {:hifi.assets/paths          [(str temp-dir "/assets")]
                             :hifi.assets/project-root   temp-dir
                             :hifi.assets/excluded-paths []}
              scanned       (scanner/scan-assets config)
              digest-infos  (map (fn [asset]
                                   (digest/digest-file-content (:abs-path asset)
                                                               (:logical-path asset)))
                                 scanned)
              manifest-data (manifest/generate-manifest digest-infos)
              manifest-path (str temp-dir "/output/manifest.edn")]

          ;; Write manifest
          (manifest/write-manifest manifest-data manifest-path)

          (testing "manifest was created"
            (is (.exists (io/file manifest-path))))

          (testing "manifest contains all assets"
            (is (= #{"js/app.js" "css/styles.css" "images/logo.png"}
                   (set (keys manifest-data)))))

          (testing "manifest entries have correct structure"
            (let [app-entry (get manifest-data "js/app.js")]
              (is (str/starts-with? (:digest-path app-entry) "app-"))
              (is (str/ends-with? (:digest-path app-entry) ".js"))
              (is (str/starts-with? (:integrity app-entry) "sha384-"))
              (is (number? (:size app-entry)))
              (is (string? (:last-modified app-entry)))))

          (testing "manifest can be loaded back"
            (is (= (get-in manifest-data ["js/app.js" :digest-path])
                   (get-in (manifest/load-manifest manifest-path) ["js/app.js" :digest-path])))))))))

(deftest asset-exists-check-test
  (testing "checking if assets exist in development mode"
    (with-temp-dir
      (fn [temp-dir]
        (fs/create-dirs (str temp-dir "/assets/js"))
        (spit (str temp-dir "/assets/js/app.js") "// code")

        (let [config    {:hifi.assets/paths [(str temp-dir "/assets")]}
              asset-ctx (assets/create-asset-context {:dev-mode? true
                                                      :config    config})]

          (is (assets/asset-exists? asset-ctx "js/app.js"))
          (is (not (assets/asset-exists? asset-ctx "js/missing.js"))))))))

(deftest path-precedence-test
  (testing "when multiple paths contain the same asset, first path wins"
    (with-temp-dir
      (fn [temp-dir]
        ;; Create two asset directories
        (fs/create-dirs (str temp-dir "/app-assets/js"))
        (fs/create-dirs (str temp-dir "/app-assets/css"))
        (fs/create-dirs (str temp-dir "/app-assets/nested/dir"))
        (fs/create-dirs (str temp-dir "/lib-assets/js"))
        (fs/create-dirs (str temp-dir "/lib-assets/css"))
        (fs/create-dirs (str temp-dir "/lib-assets/nested/dir"))

        ;; Create conflicting files with different content
        ;; Root-level conflicts
        (spit (str temp-dir "/app-assets/js/shared.js") "console.log('from app');")
        (spit (str temp-dir "/lib-assets/js/shared.js") "console.log('from lib');")

        ;; Nested directory conflicts
        (spit (str temp-dir "/app-assets/nested/dir/config.js") "// app config")
        (spit (str temp-dir "/lib-assets/nested/dir/config.js") "// lib config")

        ;; Files unique to each path
        (spit (str temp-dir "/app-assets/css/app.css") "/* app specific */")
        (spit (str temp-dir "/lib-assets/css/lib.css") "/* lib specific */")

        ;; Scan with app-assets first
        (let [scanned (scanner/scan-assets {:hifi.assets/paths          [(str temp-dir "/app-assets")
                                                                         (str temp-dir "/lib-assets")]
                                            :hifi.assets/project-root   temp-dir
                                            :hifi.assets/excluded-paths []})]

          (is (= #{"app-assets/js/shared.js" "app-assets/css/app.css" "app-assets/nested/dir/config.js" "lib-assets/css/lib.css"}
                 (set (map :relative-path scanned))))

          (is (= (str temp-dir "/app-assets/js/shared.js")
                 (:abs-path (scanner/find-asset scanned "js/shared.js"))))

          (is (= (str temp-dir "/app-assets/nested/dir/config.js")
                 (:abs-path (scanner/find-asset scanned "nested/dir/config.js"))))

          (is (= {"css/app.css" "app-assets/css/app.css"
                  "css/lib.css" "lib-assets/css/lib.css"}
                 {"css/app.css" (:relative-path (scanner/find-asset scanned "css/app.css"))
                  "css/lib.css" (:relative-path (scanner/find-asset scanned "css/lib.css"))})))

        ;; Test with reversed path order
        (let [scanned (scanner/scan-assets {:hifi.assets/paths          [(str temp-dir "/lib-assets")
                                                                         (str temp-dir "/app-assets")]
                                            :hifi.assets/project-root   temp-dir
                                            :hifi.assets/excluded-paths []})]

          (is (= #{"app-assets/css/app.css" "lib-assets/css/lib.css" "lib-assets/js/shared.js" "lib-assets/nested/dir/config.js"}
                 (set (map :relative-path scanned)))))))))
