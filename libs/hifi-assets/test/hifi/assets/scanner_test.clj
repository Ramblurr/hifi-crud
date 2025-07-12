;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

(ns hifi.assets.scanner-test
  (:require
   [clojure.test :refer [deftest testing is are]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [babashka.fs :as fs]
   [hifi.assets.scanner :as scanner]))

(defn with-temp-dir
  "Creates a temp directory and calls f with the path. Cleans up after."
  [f]
  (let [temp-dir (fs/create-temp-dir)]
    (try
      (f (str temp-dir))
      (finally
        (fs/delete-tree temp-dir)))))

(deftest directory-exists?-test
  (with-temp-dir
    (fn [temp-dir]
      (testing "returns true for existing directory"
        (is (#'scanner/directory-exists? temp-dir)))

      (testing "returns false for non-existent directory"
        (is (not (#'scanner/directory-exists? (str temp-dir "/non-existent")))))

      (testing "returns false for file"
        (let [file-path (str temp-dir "/test.txt")]
          (spit file-path "content")
          (is (not (#'scanner/directory-exists? file-path))))))))

(deftest should-exclude?-test
  (testing "excludes paths that start with excluded patterns"
    (are [path excluded-paths expected] (= expected (#'scanner/should-exclude? path excluded-paths))
      "assets/stylesheets/inputs/main.css" ["assets/stylesheets/inputs"] true
      "assets/stylesheets/inputs/admin.css" ["assets/stylesheets/inputs"] true
      "assets/stylesheets/output.css" ["assets/stylesheets/inputs"] false
      "vendor/assets/lib.js" ["vendor/assets"] true
      "vendor/lib.js" ["vendor/assets"] false
      "assets/js/app.js" ["assets/stylesheets/inputs" "assets/images/raw"] false)))

(deftest collect-files-test
  (with-temp-dir
    (fn [temp-dir]
      ;; Create test directory structure
      (fs/create-dirs (str temp-dir "/assets/js"))
      (fs/create-dirs (str temp-dir "/assets/css"))
      (fs/create-dirs (str temp-dir "/assets/images"))

      ;; Create test files
      (spit (str temp-dir "/assets/js/app.js") "console.log('app');")
      (spit (str temp-dir "/assets/js/vendor.js") "console.log('vendor');")
      (spit (str temp-dir "/assets/css/main.css") "body { margin: 0; }")
      (spit (str temp-dir "/assets/images/logo.png") "fake png data")

      (testing "collects all files recursively"
        (let [collected (#'scanner/collect-files (str temp-dir "/assets") temp-dir)]
          (is (= 4 (count collected)))

          ;; Check that all files are found
          (let [logical-paths (set (map :logical-path collected))]
            (is (contains? logical-paths "js/app.js"))
            (is (contains? logical-paths "js/vendor.js"))
            (is (contains? logical-paths "css/main.css"))
            (is (contains? logical-paths "images/logo.png")))

          ;; Check file objects are correct
          (doseq [file-info collected]
            (is (instance? java.io.File (:file file-info)))
            (is (.exists (:file file-info)))
            (is (string? (:full-path file-info)))
            (is (string? (:relative-path file-info)))
            (is (string? (:logical-path file-info)))))))))

(deftest scan-asset-paths-test
  (with-temp-dir
    (fn [temp-dir]
      ;; Create multiple asset directories
      (fs/create-dirs (str temp-dir "/assets/js"))
      (fs/create-dirs (str temp-dir "/vendor/assets/lib"))
      (fs/create-dirs (str temp-dir "/public/css"))

      ;; Create files in each directory
      (spit (str temp-dir "/assets/js/app.js") "app code")
      (spit (str temp-dir "/vendor/assets/lib/jquery.js") "jquery code")
      (spit (str temp-dir "/public/css/styles.css") "styles")

      (testing "scans multiple asset paths"
        (let [asset-paths [(str temp-dir "/assets")
                           (str temp-dir "/vendor/assets")
                           (str temp-dir "/public")]
              scanned (scanner/scan-asset-paths asset-paths [])]
          (is (= 3 (count scanned)))

          (let [logical-paths (set (map :logical-path scanned))]
            (is (contains? logical-paths "js/app.js"))
            (is (contains? logical-paths "lib/jquery.js"))
            (is (contains? logical-paths "css/styles.css")))))

      (testing "excludes specified paths"
        (let [asset-paths [(str temp-dir "/assets")
                           (str temp-dir "/vendor/assets")]
              excluded-paths [(str temp-dir "/vendor/assets")]
              scanned (scanner/scan-asset-paths asset-paths excluded-paths)]
          (is (= 1 (count scanned)))
          (is (= "js/app.js" (:logical-path (first scanned))))))

      (testing "handles non-existent paths gracefully"
        (let [asset-paths [(str temp-dir "/non-existent")
                           (str temp-dir "/assets")]
              scanned (scanner/scan-asset-paths asset-paths [])]
          (is (= 1 (count scanned)))
          (is (= "js/app.js" (:logical-path (first scanned))))))

      (testing "returns empty when no valid paths"
        (let [scanned (scanner/scan-asset-paths [(str temp-dir "/non-existent")] [])]
          (is (empty? scanned)))))))

(deftest scan-assets-from-config-test
  (with-temp-dir
    (fn [temp-dir]
      (fs/create-dirs (str temp-dir "/assets"))
      (spit (str temp-dir "/assets/test.js") "test")

      (testing "scans using config map"
        (let [config {:hifi/assets {:paths [(str temp-dir "/assets")]
                                    :excluded-paths []}}
              scanned (scanner/scan-assets-from-config config)]
          (is (= 1 (count scanned)))
          (is (= "test.js" (:logical-path (first scanned)))))))))

(deftest group-by-extension-test
  (let [scanned-assets [{:file (io/file "app.js") :logical-path "app.js"}
                        {:file (io/file "vendor.js") :logical-path "vendor.js"}
                        {:file (io/file "main.css") :logical-path "main.css"}
                        {:file (io/file "logo.png") :logical-path "logo.png"}
                        {:file (io/file "README") :logical-path "README"}]]
    (testing "groups assets by file extension"
      (let [grouped (scanner/group-by-extension scanned-assets)]
        (is (= 2 (count (get grouped "js"))))
        (is (= 1 (count (get grouped "css"))))
        (is (= 1 (count (get grouped "png"))))
        (is (= 1 (count (get grouped "")))) ;; No extension
        (is (= #{"app.js" "vendor.js"}
               (set (map :logical-path (get grouped "js")))))))))

(deftest filter-by-extension-test
  (let [scanned-assets [{:file (io/file "app.js") :logical-path "app.js"}
                        {:file (io/file "vendor.js") :logical-path "vendor.js"}
                        {:file (io/file "main.css") :logical-path "main.css"}
                        {:file (io/file "logo.png") :logical-path "logo.png"}]]
    (testing "filters assets by single extension"
      (let [filtered (scanner/filter-by-extension scanned-assets ["js"])]
        (is (= 2 (count filtered)))
        (is (every? #(str/ends-with? (:logical-path %) ".js") filtered))))

    (testing "filters assets by multiple extensions"
      (let [filtered (scanner/filter-by-extension scanned-assets ["js" "css"])]
        (is (= 3 (count filtered)))
        (is (every? #(or (str/ends-with? (:logical-path %) ".js")
                         (str/ends-with? (:logical-path %) ".css"))
                    filtered))))))

(deftest asset-info-test
  (with-temp-dir
    (fn [temp-dir]
      (let [file-path (str temp-dir "/test.txt")
            _ (spit file-path "test content")
            scanned-asset {:file (io/file file-path)
                           :full-path file-path
                           :relative-path "test.txt"
                           :logical-path "test.txt"}
            info (scanner/asset-info scanned-asset)]

        (testing "returns detailed asset information"
          (is (= (io/file file-path) (:file info)))
          (is (= file-path (:full-path info)))
          (is (= "test.txt" (:relative-path info)))
          (is (= "test.txt" (:logical-path info)))
          (is (= "test.txt" (:filename info)))
          (is (= 12 (:size info))) ;; "test content" = 12 bytes
          (is (number? (:last-modified info)))
          (is (= "txt" (:extension info))))

        (testing "handles files without extension"
          (let [no-ext-path (str temp-dir "/README")
                _ (spit no-ext-path "readme")
                no-ext-asset {:file (io/file no-ext-path)
                              :full-path no-ext-path
                              :relative-path "README"
                              :logical-path "README"}
                no-ext-info (scanner/asset-info no-ext-asset)]
            (is (nil? (:extension no-ext-info)))))))))

(deftest find-asset-test
  (let [scanned-assets [{:logical-path "app.js" :file (io/file "app.js")}
                        {:logical-path "css/main.css" :file (io/file "main.css")}
                        {:logical-path "images/logo.png" :file (io/file "logo.png")}]]

    (testing "finds asset by logical path"
      (let [found (scanner/find-asset scanned-assets "css/main.css")]
        (is (= "css/main.css" (:logical-path found)))
        (is (= (io/file "main.css") (:file found)))))

    (testing "returns nil when not found"
      (is (nil? (scanner/find-asset scanned-assets "non-existent.js"))))))

(deftest integration-test
  (testing "complete scanning workflow with real files"
    (with-temp-dir
      (fn [temp-dir]
        ;; Create a realistic asset structure
        (fs/create-dirs (str temp-dir "/assets/javascripts"))
        (fs/create-dirs (str temp-dir "/assets/stylesheets/inputs"))
        (fs/create-dirs (str temp-dir "/assets/stylesheets"))
        (fs/create-dirs (str temp-dir "/assets/images"))
        (fs/create-dirs (str temp-dir "/vendor/assets"))

        ;; Create various files
        (spit (str temp-dir "/assets/javascripts/app.js") "// app js")
        (spit (str temp-dir "/assets/javascripts/admin.js") "// admin js")
        (spit (str temp-dir "/assets/stylesheets/app.css") "/* app css */")
        (spit (str temp-dir "/assets/stylesheets/inputs/tailwind.css") "/* tailwind input */")
        (spit (str temp-dir "/assets/images/logo.png") "fake png")
        (spit (str temp-dir "/vendor/assets/jquery.js") "// jquery")

        ;; Scan with exclusions
        (let [config {:hifi/assets {:paths [(str temp-dir "/assets")
                                            (str temp-dir "/vendor/assets")]
                                    :excluded-paths [(str temp-dir "/assets/stylesheets/inputs")]}}
              scanned (scanner/scan-assets-from-config config)
              logical-paths (set (map :logical-path scanned))]

          ;; Should include these
          (is (contains? logical-paths "javascripts/app.js"))
          (is (contains? logical-paths "javascripts/admin.js"))
          (is (contains? logical-paths "stylesheets/app.css"))
          (is (contains? logical-paths "images/logo.png"))
          (is (contains? logical-paths "jquery.js"))

          ;; Should exclude this
          (is (not (contains? logical-paths "stylesheets/inputs/tailwind.css")))

          ;; Test filtering
          (let [js-files (scanner/filter-by-extension scanned ["js"])]
            (is (= 3 (count js-files))))

          ;; Test grouping
          (let [grouped (scanner/group-by-extension scanned)]
            (is (= 3 (count (get grouped "js"))))
            (is (= 1 (count (get grouped "css"))))
            (is (= 1 (count (get grouped "png")))))

          ;; Test finding specific asset
          (let [app-js (scanner/find-asset scanned "javascripts/app.js")]
            (is (some? app-js))
            (is (= "javascripts/app.js" (:logical-path app-js)))
            (is (.exists (:file app-js)))))))))