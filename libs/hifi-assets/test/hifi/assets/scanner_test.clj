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
        (is (scanner/directory-exists? temp-dir)))

      (testing "returns false for non-existent directory"
        (is (not (scanner/directory-exists? (str temp-dir "/non-existent")))))

      (testing "returns false for file"
        (let [file-path (str temp-dir "/test.txt")]
          (spit file-path "content")
          (is (not (scanner/directory-exists? file-path))))))))

(deftest should-exclude?-test
  (testing "excludes paths that start with excluded patterns"
    (are [path excluded-paths expected] (= expected (scanner/should-exclude? path excluded-paths))
      "assets/stylesheets/inputs/main.css"  ["assets/stylesheets/inputs"]                     true
      "assets/stylesheets/inputs/admin.css" ["assets/stylesheets/inputs"]                     true
      "assets/stylesheets/output.css"       ["assets/stylesheets/inputs"]                     false
      "vendor/assets/lib.js"                ["vendor/assets"]                                 true
      "vendor/lib.js"                       ["vendor/assets"]                                 false
      "assets/js/app.js"                    ["assets/stylesheets/inputs" "assets/images/raw"] false)))

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
        (let [collected (scanner/collect-files (str temp-dir "/assets") temp-dir)]
          (is (= 4 (count collected)))
          (is (= #{{:abs-path (str temp-dir "/assets/js/app.js")
                    :relative-path "assets/js/app.js"
                    :file (io/file (str temp-dir "/assets/js/app.js"))
                    :logical-path "js/app.js"}
                   {:abs-path (str temp-dir "/assets/js/vendor.js")
                    :relative-path "assets/js/vendor.js"
                    :file (io/file (str temp-dir "/assets/js/vendor.js"))
                    :logical-path "js/vendor.js"}
                   {:abs-path (str temp-dir "/assets/css/main.css")
                    :relative-path "assets/css/main.css"
                    :file (io/file (str temp-dir "/assets/css/main.css"))
                    :logical-path "css/main.css"}
                   {:abs-path (str temp-dir "/assets/images/logo.png")
                    :relative-path "assets/images/logo.png"
                    :file (io/file (str temp-dir "/assets/images/logo.png"))
                    :logical-path "images/logo.png"}}
                 (set collected))))))))

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

      (testing "degenerate case"
        (is (= [] (scanner/scan-assets [] {:hifi.assets/excluded-paths [] :hifi.assets/project-root temp-dir}))))

      (testing "scans multiple asset paths"
        (let [asset-paths [(str temp-dir "/assets")
                           (str temp-dir "/vendor/assets")
                           (str temp-dir "/public")]
              scanned     (scanner/scan-assets asset-paths {:hifi.assets/excluded-paths [] :hifi.assets/project-root temp-dir})]
          (is (= #{{:abs-path (str temp-dir "/assets/js/app.js")
                    :relative-path "assets/js/app.js"
                    :file (io/file (str temp-dir "/assets/js/app.js"))
                    :logical-path "js/app.js"}
                   {:abs-path (str temp-dir "/vendor/assets/lib/jquery.js")
                    :relative-path "vendor/assets/lib/jquery.js"
                    :file (io/file (str temp-dir "/vendor/assets/lib/jquery.js"))
                    :logical-path "lib/jquery.js"}
                   {:abs-path (str temp-dir "/public/css/styles.css")
                    :relative-path "public/css/styles.css"
                    :file (io/file (str temp-dir "/public/css/styles.css"))
                    :logical-path "css/styles.css"}}
                 (set scanned)))))

      (testing "excludes specified paths"
        (let [asset-paths    [(str temp-dir "/assets")
                              (str temp-dir "/vendor/assets")]
              excluded-paths [(str temp-dir "/vendor/assets")]
              scanned        (scanner/scan-assets asset-paths {:hifi.assets/excluded-paths excluded-paths :hifi.assets/project-root temp-dir})]
          (is (= [{:abs-path (str temp-dir "/assets/js/app.js")
                   :relative-path "assets/js/app.js"
                   :file (io/file (str temp-dir "/assets/js/app.js"))
                   :logical-path "js/app.js"}]
                 scanned))))

      (testing "handles non-existent paths gracefully"
        (let [asset-paths [(str temp-dir "/non-existent")
                           (str temp-dir "/assets")]
              scanned     (scanner/scan-assets asset-paths {:hifi.assets/excluded-paths [] :hifi.assets/project-root temp-dir})]
          (is (= [{:abs-path (str temp-dir "/assets/js/app.js")
                   :relative-path "assets/js/app.js"
                   :file (io/file (str temp-dir "/assets/js/app.js"))
                   :logical-path "js/app.js"}]
                 scanned))))

      (testing "returns empty when no valid paths"
        (let [scanned (scanner/scan-assets [(str temp-dir "/non-existent")] {:hifi.assets/excluded-paths [] :hifi.assets/project-root temp-dir})]
          (is (= [] scanned)))))))

(deftest group-by-extension-test
  (let [scanned-assets [{:file (io/file "app.js") :logical-path "app.js"}
                        {:file (io/file "vendor.js") :logical-path "vendor.js"}
                        {:file (io/file "main.css") :logical-path "main.css"}
                        {:file (io/file "logo.png") :logical-path "logo.png"}
                        {:file (io/file "README") :logical-path "README"}]]
    (testing "groups assets by file extension"
      (is (= {"js" [{:file (io/file "app.js") :logical-path "app.js"}
                    {:file (io/file "vendor.js") :logical-path "vendor.js"}]
              "css" [{:file (io/file "main.css") :logical-path "main.css"}]
              "png" [{:file (io/file "logo.png") :logical-path "logo.png"}]
              "" [{:file (io/file "README") :logical-path "README"}]}
             (scanner/group-by-extension scanned-assets))))))

(deftest filter-by-extension-test
  (let [scanned-assets [{:file (io/file "app.js") :logical-path "app.js"}
                        {:file (io/file "vendor.js") :logical-path "vendor.js"}
                        {:file (io/file "main.css") :logical-path "main.css"}
                        {:file (io/file "logo.png") :logical-path "logo.png"}]]
    (testing "filters assets by single extension"
      (is (= [{:file (io/file "app.js") :logical-path "app.js"}
              {:file (io/file "vendor.js") :logical-path "vendor.js"}]
             (scanner/filter-by-extension scanned-assets ["js"]))))

    (testing "filters assets by multiple extensions"
      (is (= [{:file (io/file "app.js") :logical-path "app.js"}
              {:file (io/file "vendor.js") :logical-path "vendor.js"}
              {:file (io/file "main.css") :logical-path "main.css"}]
             (scanner/filter-by-extension scanned-assets ["js" "css"]))))))

(deftest asset-info-test
  (with-temp-dir
    (fn [temp-dir]
      (testing "returns detailed asset information"
        (let [file-path     (str temp-dir "/test.txt")
              _             (spit file-path "test content")
              scanned-asset {:file          (io/file file-path)
                             :abs-path      file-path
                             :relative-path "test.txt"
                             :logical-path  "test.txt"}
              info          (scanner/asset-info scanned-asset)]
          (is (= (-> scanned-asset
                     (assoc :filename "test.txt"
                            :size 12
                            :extension "txt")
                     (dissoc :last-modified))
                 (dissoc info :last-modified)))
          (is (number? (:last-modified info)))))

      (testing "handles files without extension"
        (let [no-ext-path  (str temp-dir "/README")
              _            (spit no-ext-path "readme")
              no-ext-asset {:file          (io/file no-ext-path)
                            :abs-path      no-ext-path
                            :relative-path "README"
                            :logical-path  "README"}
              no-ext-info  (scanner/asset-info no-ext-asset)]
          (is (= (-> no-ext-asset
                     (assoc :filename "README"
                            :size 6
                            :extension nil)
                     (dissoc :last-modified))
                 (dissoc no-ext-info :last-modified)))
          (is (number? (:last-modified no-ext-info))))))))

(deftest find-asset-test
  (let [scanned-assets [{:logical-path "app.js" :file (io/file "app.js")}
                        {:logical-path "css/main.css" :file (io/file "main.css")}
                        {:logical-path "images/logo.png" :file (io/file "logo.png")}]]

    (testing "finds asset by logical path"
      (is (= {:logical-path "css/main.css" :file (io/file "main.css")}
             (scanner/find-asset scanned-assets "css/main.css"))))

    (testing "returns nil when not found"
      (is (= nil (scanner/find-asset scanned-assets "non-existent.js"))))))

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
        (let [config  {:hifi.assets/paths          [(str temp-dir "/assets")
                                                    (str temp-dir "/vendor/assets")]
                       :hifi.assets/project-root   temp-dir
                       :hifi.assets/excluded-paths [(str temp-dir "/assets/stylesheets/inputs")]}
              scanned (scanner/scan-assets config)]

          ;; Check logical paths (excludes inputs/tailwind.css)
          (is (= #{"javascripts/app.js" "javascripts/admin.js"
                   "stylesheets/app.css" "images/logo.png" "jquery.js"}
                 (set (map :logical-path scanned))))

          ;; Test filtering by extension
          (is (= #{"javascripts/app.js" "javascripts/admin.js" "jquery.js"}
                 (set (map :logical-path (scanner/filter-by-extension scanned ["js"])))))

          ;; Test grouping by extension
          (is (= {"js" 3 "css" 1 "png" 1}
                 (update-vals (scanner/group-by-extension scanned) count)))

          ;; Test finding specific asset
          (is (= "javascripts/app.js"
                 (:logical-path (scanner/find-asset scanned "javascripts/app.js")))))))))
