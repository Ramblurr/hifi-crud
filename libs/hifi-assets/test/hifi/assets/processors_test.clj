;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets.processors-test
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [hifi.assets.digest :as digest]
   [hifi.assets.manifest :as manifest]
   [hifi.assets.processors :as processors]
   [hifi.assets.process :as process]
   [hifi.assets.scanner :as scanner]))

(defn with-temp-dir [f]
  (let [temp-dir (fs/create-temp-dir)]
    (try
      (f (str temp-dir))
      (finally
        (fs/delete-tree temp-dir)))))

(deftest css-asset-dependencies-test
  (testing "finds CSS url() dependencies"
    (let [css-content "body { background: url(images/bg.png); }
                       .logo { background-image: url('./assets/logo.svg'); }
                       .hero { background: url('../shared/hero.jpg#section'); }
                       .external { background: url('http://example.com/bg.jpg'); }
                       .data { background: url('data:image/svg+xml,<svg>...</svg>'); }"
          asset {:logical-path "css/main.css"}
          deps (processors/css-dependencies {} asset css-content)]
      (is (= #{"css/images/bg.png" "css/assets/logo.svg" "shared/hero.jpg"} deps)))))

(deftest js-asset-dependencies-test
  (testing "finds JavaScript HIFI_ASSET_URL dependencies"
    (let [js-content "const logo = HIFI_ASSET_URL('images/logo.png');
                      const config = HIFI_ASSET_URL('./config/app.json');
                      const shared = HIFI_ASSET_URL('../shared/data.json');
                      const external = 'http://example.com/api';"
          asset {:logical-path "js/app.js"}
          deps (processors/js-dependencies {} asset js-content)]
      (is (= #{"js/images/logo.png" "js/config/app.json" "shared/data.json"} deps)))))

(deftest css-processor-test
  (testing "basic url() processing"
    (let [css-content "{ background: url(file.jpg); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"foobar/source/file.jpg" {:digest-path "foobar/source/file-abc123.jpg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/source/file.jpg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/foobar/source/file-abc123.jpg\")"))))

  (testing "blank spaces around name"
    (let [css-content "{ background: url( file.jpg ); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"foobar/source/file.jpg" {:digest-path "foobar/source/file-abc123.jpg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/source/file.jpg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/foobar/source/file-abc123.jpg\")"))))

  (testing "quotes around name"
    (let [css-content "{ background: url(\"file.jpg\"); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"foobar/source/file.jpg" {:digest-path "foobar/source/file-abc123.jpg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/source/file.jpg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/foobar/source/file-abc123.jpg\")"))))

  (testing "single quotes around name"
    (let [css-content "{ background: url('file.jpg'); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"foobar/source/file.jpg" {:digest-path "foobar/source/file-abc123.jpg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/source/file.jpg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/foobar/source/file-abc123.jpg\")"))))

  (testing "root directory"
    (let [css-content "{ background: url('/file.jpg'); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"file.jpg" {:digest-path "file-abc123.jpg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "file.jpg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/file-abc123.jpg\")"))))

  (testing "same directory"
    (let [css-content "{ background: url('./file.jpg'); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"foobar/source/file.jpg" {:digest-path "foobar/source/file-abc123.jpg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/source/file.jpg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/foobar/source/file-abc123.jpg\")"))))

  (testing "subdirectory"
    (let [css-content "{ background: url('./images/file.jpg'); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"foobar/source/images/file.jpg" {:digest-path "foobar/source/images/file-abc123.jpg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/source/images/file.jpg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/foobar/source/images/file-abc123.jpg\")"))))

  (testing "parent directory"
    (let [css-content "{ background: url('../file.jpg'); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"foobar/file.jpg" {:digest-path "foobar/file-abc123.jpg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/file.jpg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/foobar/file-abc123.jpg\")"))))

  (testing "grandparent directory"
    (let [css-content "{ background: url('../../file.jpg'); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"file.jpg" {:digest-path "file-abc123.jpg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "file.jpg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/file-abc123.jpg\")"))))

  (testing "sibling directory"
    (let [css-content "{ background: url('../sibling/file.jpg'); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"foobar/sibling/file.jpg" {:digest-path "foobar/sibling/file-abc123.jpg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/sibling/file.jpg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/foobar/sibling/file-abc123.jpg\")"))))

  (testing "multiple url() references"
    (let [css-content "{ content: url(file.svg) url(file.svg); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"foobar/source/file.svg" {:digest-path "foobar/source/file-abc123.svg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/source/file.svg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (= (count (re-seq #"url\(\"/assets/foobar/source/file-abc123\.svg\"\)" (:content processed))) 2))))

  (testing "external URLs are not processed"
    (let [css-content "{ background: url('https://rubyonrails.org/images/rails-logo.svg'); }"
          asset {:logical-path "test.css"}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest {}
               :find-asset (constantly nil)}
          processed (processors/css-processor ctx asset css-content)]
      (is (= "{ background: url('https://rubyonrails.org/images/rails-logo.svg'); }" (:content processed)))))

  (testing "relative protocol URLs are not processed"
    (let [css-content "{ background: url('//rubyonrails.org/images/rails-logo.svg'); }"
          asset {:logical-path "test.css"}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest {}
               :find-asset (constantly nil)}
          processed (processors/css-processor ctx asset css-content)]
      (is (= "{ background: url('//rubyonrails.org/images/rails-logo.svg'); }" (:content processed)))))

  (testing "data URLs are not processed"
    (let [css-content "{ background: url(data:image/png;base64,iRxVB0); }"
          asset {:logical-path "test.css"}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest {}
               :find-asset (constantly nil)}
          processed (processors/css-processor ctx asset css-content)]
      (is (= "{ background: url(data:image/png;base64,iRxVB0); }" (:content processed)))))

  (testing "anchor links are not processed"
    (let [css-content "{ background: url(#IDofSVGpath); }"
          asset {:logical-path "test.css"}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest {}
               :find-asset (constantly nil)}
          processed (processors/css-processor ctx asset css-content)]
      (is (= "{ background: url(#IDofSVGpath); }" (:content processed)))))

  (testing "svg anchor with asset"
    (let [css-content "{ content: url(file.svg#rails); }"
          asset {:logical-path "foobar/source/test.css"}
          manifest {"foobar/source/file.svg" {:digest-path "foobar/source/file-abc123.svg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/source/file.svg") {:logical-path path}))}
          processed (processors/css-processor ctx asset css-content)]
      (is (str/includes? (:content processed) "url(\"/assets/foobar/source/file-abc123.svg#rails\")"))))

  (testing "missing asset falls back to original"
    (let [css-content "{ background: url(\"file-not-found.jpg\"); }"
          asset {:logical-path "test.css"}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest {}
               :find-asset (constantly nil)}
          processed (processors/css-processor ctx asset css-content)]
      (is (= "{ background: url(\"file-not-found.jpg\"); }" (:content processed))))))

(deftest js-processor-test
  (testing "basic HIFI_ASSET_URL processing"
    (let [js-content "this.img = HIFI_ASSET_URL('/foobar/source/file.svg');"
          asset {:logical-path "foobar/source/test.js"}
          manifest {"foobar/source/file.svg" {:digest-path "foobar/source/file-abc123.svg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "foobar/source/file.svg") {:logical-path path}))}
          processed (processors/js-processor ctx asset js-content)]
      (is (str/includes? (:content processed) "\"/assets/foobar/source/file-abc123.svg\""))))

  (testing "HIFI_ASSET_URL with relative path"
    (let [js-content "const logo = HIFI_ASSET_URL('images/logo.png');"
          asset {:logical-path "js/app.js"}
          manifest {"js/images/logo.png" {:digest-path "js/images/logo-xyz789.png"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "js/images/logo.png") {:logical-path path}))}
          processed (processors/js-processor ctx asset js-content)]
      (is (= "const logo = \"/assets/js/images/logo-xyz789.png\";" (:content processed)))))

  (testing "missing asset falls back to original"
    (let [js-content "this.img = HIFI_ASSET_URL(\"missing.svg\");"
          asset {:logical-path "js/test.js"}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest {}
               :find-asset (constantly nil)}
          processed (processors/js-processor ctx asset js-content)]
      (is (= "this.img = \"missing.svg\";" (:content processed)))))

  (testing "HIFI_ASSET_URL with fragment"
    (let [js-content "const icon = HIFI_ASSET_URL('icons/sprite.svg#icon-home');"
          asset {:logical-path "js/app.js"}
          manifest {"js/icons/sprite.svg" {:digest-path "js/icons/sprite-def456.svg"}}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest manifest
               :find-asset (fn [path] (when (= path "js/icons/sprite.svg") {:logical-path path}))}
          processed (processors/js-processor ctx asset js-content)]
      (is (= "const icon = \"/assets/js/icons/sprite-def456.svg#icon-home\";" (:content processed)))))

  (testing "external URLs are not processed"
    (let [js-content "const external = 'http://example.com/api';"
          asset {:logical-path "js/app.js"}
          ctx {:config {:hifi.assets/prefix "/assets"}
               :manifest {}
               :find-asset (constantly nil)}
          processed (processors/js-processor ctx asset js-content)]
      (is (= "const external = 'http://example.com/api';" (:content processed))))))

(deftest process-assets-integration-test
  (testing "complete asset processing pipeline"
    (with-temp-dir
      (fn [temp-dir]
        (fs/create-dirs (str temp-dir "/assets/css"))
        (fs/create-dirs (str temp-dir "/assets/js"))
        (fs/create-dirs (str temp-dir "/assets/images"))

        (spit (str temp-dir "/assets/css/main.css")
              "body { background: url(../images/bg.png); }")
        (spit (str temp-dir "/assets/js/app.js")
              "const logo = HIFI_ASSET_URL('../images/logo.png');")
        (spit (str temp-dir "/assets/images/bg.png") "fake png")
        (spit (str temp-dir "/assets/images/logo.png") "fake logo")

        (let [config {:hifi.assets/paths [(str temp-dir "/assets")]
                      :hifi.assets/project-root temp-dir
                      :hifi.assets/excluded-paths []
                      :hifi.assets/prefix "/assets"
                      :hifi.assets/processors [{:mime-types #{"text/css"}
                                                :dependencies processors/css-dependencies
                                                :process processors/css-processor}
                                               {:mime-types #{"application/javascript"}
                                                :dependencies processors/js-dependencies
                                                :process processors/js-processor}]}
              scanned (scanner/scan-assets config)
              digest-infos (map #(digest/digest-file-content (:abs-path %) (:logical-path %)) scanned)
              manifest-data (manifest/generate-manifest digest-infos)
              ctx {:config config :manifest manifest-data
                   :find-asset (fn [path] (scanner/find-asset scanned path))}
              processing-result (process/process-assets ctx scanned)
              processed (:assets processing-result)]

          (is (= 4 (count processed)))

          (let [css-asset (first (filter #(= "css/main.css" (:logical-path %)) processed))
                js-asset (first (filter #(= "js/app.js" (:logical-path %)) processed))
                bg-digest-path (:digest-path (get manifest-data "images/bg.png"))
                logo-digest-path (:digest-path (get manifest-data "images/logo.png"))]
            (is (= (str "body { background: url(\"/assets/" bg-digest-path "\"); }")
                   (:processed-content css-asset)))
            (is (= (str "const logo = \"/assets/" logo-digest-path "\";")
                   (:processed-content js-asset)))))))))

(deftest process-assets-order-test
  (with-temp-dir
    (fn [temp-dir]
      (fs/create-dirs (str temp-dir "/assets/css"))
      (fs/create-dirs (str temp-dir "/assets/images"))

      ;; Create files with dependencies
      (spit (str temp-dir "/assets/css/app.css")
            "body { background: url(../images/bg.png); }")
      (spit (str temp-dir "/assets/images/bg.png") "fake png")

      (let [config {:hifi.assets/paths [(str temp-dir "/assets")]
                    :hifi.assets/project-root temp-dir
                    :hifi.assets/excluded-paths []
                    :hifi.assets/prefix "/assets"
                    :hifi.assets/processors [{:mime-types #{"text/css"}
                                              :dependencies processors/css-dependencies
                                              :process processors/css-processor}]}
            scanned (scanner/scan-assets config)
            digest-infos (map #(digest/digest-file-content (:abs-path %) (:logical-path %)) scanned)
            manifest-data (manifest/generate-manifest digest-infos)
            ctx {:config config
                 :manifest manifest-data
                 :find-asset (fn [path] (scanner/find-asset scanned path))}
            processing-result (process/process-assets ctx scanned)
            processed (:assets processing-result)
            css-asset (first (filter #(= "css/app.css" (:logical-path %)) processed))
            bg-digest-path (:digest-path (get manifest-data "images/bg.png"))]

        (is (some? css-asset) "CSS asset should be processed")
        (is (str/includes? (:processed-content css-asset) (str "/assets/" bg-digest-path))
            "CSS should reference digested image path")
        (is (str/includes? (:processed-content css-asset) "url(\"/assets/")
            "CSS should contain processed URL")))))
