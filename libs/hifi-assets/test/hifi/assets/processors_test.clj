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

(deftest source-mapping-dependencies-test
  (testing "finds source mapping dependencies in JavaScript"
    (let [js-content "console.log('test');\n//# sourceMappingURL=app.js.map"
          asset {:logical-path "js/app.js"}
          deps (processors/source-mapping-dependencies {} asset js-content)]
      (is (= #{"js/app.js.map"} deps))))

  (testing "finds source mapping dependencies in CSS"
    (let [css-content "body { color: red; }\n/*# sourceMappingURL=styles.css.map */"
          asset {:logical-path "css/styles.css"}
          deps (processors/source-mapping-dependencies {} asset css-content)]
      (is (= #{"css/styles.css.map"} deps))))

  (testing "resolves relative paths correctly"
    (let [js-content "//# sourceMappingURL=../maps/app.js.map"
          asset {:logical-path "js/nested/app.js"}
          deps (processors/source-mapping-dependencies {} asset js-content)]
      (is (= #{"js/maps/app.js.map"} deps))))

  (testing "returns empty set when no source mappings"
    (let [js-content "console.log('test');"
          asset {:logical-path "js/app.js"}
          deps (processors/source-mapping-dependencies {} asset js-content)]
      (is (= #{} deps)))))

(deftest source-mapping-processor-test
  (testing "processes JavaScript source mapping URL with existing map"
    (let [js-content "console.log('test');\n//# sourceMappingURL=app.js.map"
          asset {:logical-path "js/app.js"}
          manifest {"js/app.js.map" {:digest-path "js/app-abc123.js.map"}}
          ctx {:config {:hifi.assets/prefix "/assets"} :manifest manifest}
          processed (processors/source-mapping-processor ctx asset js-content)]
      (is (= "console.log('test');\n//# sourceMappingURL=/assets/js/app-abc123.js.map"
             (:content processed)))
      (is (= [] (:warnings processed)))))

  (testing "processes CSS source mapping URL with existing map"
    (let [css-content "body { color: red; }\n/*# sourceMappingURL=styles.css.map */"
          asset {:logical-path "css/styles.css"}
          manifest {"css/styles.css.map" {:digest-path "css/styles-xyz789.css.map"}}
          ctx {:config {:hifi.assets/prefix "/assets"} :manifest manifest}
          processed (processors/source-mapping-processor ctx asset css-content)]
      (is (= "body { color: red; }\n/*# sourceMappingURL=/assets/css/styles-xyz789.css.map */"
             (:content processed)))
      (is (= [] (:warnings processed)))))

  (testing "removes source mapping URL when map is missing"
    (let [js-content "console.log('test');\n//# sourceMappingURL=missing.js.map"
          asset {:logical-path "js/app.js"}
          manifest {}
          ctx {:config {:hifi.assets/prefix "/assets"} :manifest manifest}
          processed (processors/source-mapping-processor ctx asset js-content)]
      (is (= "console.log('test');\n//" (:content processed)))
      (is (= [{:type :missing-source-map
               :asset "js/app.js"
               :missing-path "js/missing.js.map"
               :original-path "missing.js.map"}]
             (:warnings processed)))))

  (testing "removes CSS source mapping URL when map is missing but preserves comment structure"
    (let [css-content "body { color: red; }\n/*# sourceMappingURL=missing.css.map */"
          asset {:logical-path "css/styles.css"}
          manifest {}
          ctx {:config {:hifi.assets/prefix "/assets"} :manifest manifest}
          processed (processors/source-mapping-processor ctx asset css-content)]
      (is (= "body { color: red; }\n/* */" (:content processed)))
      (is (= [{:type :missing-source-map
               :asset "css/styles.css"
               :missing-path "css/missing.css.map"
               :original-path "missing.css.map"}]
             (:warnings processed)))))

  (testing "handles relative source mapping paths"
    (let [js-content "//# sourceMappingURL=../maps/nested.js.map"
          asset {:logical-path "js/nested/app.js"}
          manifest {"js/maps/nested.js.map" {:digest-path "js/maps/nested-def456.js.map"}}
          ctx {:config {:hifi.assets/prefix "/assets"} :manifest manifest}
          processed (processors/source-mapping-processor ctx asset js-content)]
      (is (= "//# sourceMappingURL=/assets/js/maps/nested-def456.js.map"
             (:content processed)))
      (is (= [] (:warnings processed)))))

  (testing "only processes source mapping URLs at end of file"
    (let [js-content "//# sourceMappingURL=should-not-process.js.map\nconsole.log('more code');"
          asset {:logical-path "js/app.js"}
          manifest {}
          ctx {:config {:hifi.assets/prefix "/assets"} :manifest manifest}
          processed (processors/source-mapping-processor ctx asset js-content)]
      (is (= js-content (:content processed)))
      (is (= [] (:warnings processed))))))

(deftest complete-asset-processing-test
  (testing "complete asset processing pipeline with all processors"
    (with-temp-dir
      (fn [temp-dir]
        (fs/create-dirs (str temp-dir "/assets/css"))
        (fs/create-dirs (str temp-dir "/assets/js"))
        (fs/create-dirs (str temp-dir "/assets/images"))

        (spit (str temp-dir "/assets/css/main.css")
              "body { background: url(../images/bg.png); }\n/*# sourceMappingURL=main.css.map */")
        (spit (str temp-dir "/assets/css/main.css.map")
              "{\"version\":3,\"sources\":[\"main.scss\"]}")
        (spit (str temp-dir "/assets/js/app.js")
              "const logo = HIFI_ASSET_URL('../images/logo.png');\nconsole.log('loaded');\n//# sourceMappingURL=app.js.map")
        (spit (str temp-dir "/assets/js/app.js.map")
              "{\"version\":3,\"sources\":[\"app.ts\"]}")
        (spit (str temp-dir "/assets/images/bg.png") "fake png")
        (spit (str temp-dir "/assets/images/logo.png") "fake logo")

        (let [config {:hifi.assets/paths [(str temp-dir "/assets")]
                      :hifi.assets/project-root temp-dir
                      :hifi.assets/excluded-paths []
                      :hifi.assets/prefix "/assets"
                      :hifi.assets/processors processors/default-processors}
              scanned (scanner/scan-assets config)
              digest-infos (map #(digest/digest-file-content (:abs-path %) (:logical-path %)) scanned)
              manifest-data (manifest/generate-manifest digest-infos)
              ctx {:config config :manifest manifest-data
                   :find-asset (fn [path] (scanner/find-asset scanned path))}
              processing-result (process/process-assets ctx scanned)
              processed (:assets processing-result)]

          (testing "processes all assets"
            (is (= 6 (count processed))))

          (testing "CSS url() processing"
            (let [css-asset (first (filter #(= "css/main.css" (:logical-path %)) processed))
                  bg-digest-path (:digest-path (get manifest-data "images/bg.png"))
                  css-map-digest-path (:digest-path (get manifest-data "css/main.css.map"))]
              (is (str/includes? (:processed-content css-asset)
                                 (str "url(\"/assets/" bg-digest-path "\")")))
              (is (str/includes? (:processed-content css-asset)
                                 (str "/*# sourceMappingURL=/assets/" css-map-digest-path " */")))))

          (testing "JavaScript HIFI_ASSET_URL processing"
            (let [js-asset (first (filter #(= "js/app.js" (:logical-path %)) processed))
                  logo-digest-path (:digest-path (get manifest-data "images/logo.png"))
                  js-map-digest-path (:digest-path (get manifest-data "js/app.js.map"))]
              (is (str/includes? (:processed-content js-asset)
                                 (str "\"/assets/" logo-digest-path "\"")))
              (is (str/includes? (:processed-content js-asset)
                                 (str "//# sourceMappingURL=/assets/" js-map-digest-path)))))

          (testing "source map files are not processed"
            (let [css-map-asset (first (filter #(= "css/main.css.map" (:logical-path %)) processed))
                  js-map-asset (first (filter #(= "js/app.js.map" (:logical-path %)) processed))]
              (is (= "{\"version\":3,\"sources\":[\"main.scss\"]}" (:processed-content css-map-asset)))
              (is (= "{\"version\":3,\"sources\":[\"app.ts\"]}" (:processed-content js-map-asset)))))

          (testing "no processing warnings"
            (is (empty? (:warnings processing-result))))))))

  (testing "missing source map handling"
    (with-temp-dir
      (fn [temp-dir]
        (fs/create-dirs (str temp-dir "/assets/js"))
        (spit (str temp-dir "/assets/js/broken.js")
              "console.log('test');\n//# sourceMappingURL=missing.js.map")

        (let [config {:hifi.assets/paths [(str temp-dir "/assets")]
                      :hifi.assets/project-root temp-dir
                      :hifi.assets/excluded-paths []
                      :hifi.assets/prefix "/assets"
                      :hifi.assets/processors processors/default-processors}
              scanned (scanner/scan-assets config)
              digest-infos (map #(digest/digest-file-content (:abs-path %) (:logical-path %)) scanned)
              manifest-data (manifest/generate-manifest digest-infos)
              ctx {:config config :manifest manifest-data
                   :find-asset (fn [path] (scanner/find-asset scanned path))}
              processing-result (process/process-assets ctx scanned)
              processed (:assets processing-result)]

          (testing "removes missing source mapping URL"
            (let [js-asset (first (filter #(= "js/broken.js" (:logical-path %)) processed))]
              (is (= "console.log('test');\n//" (:processed-content js-asset)))))

          (is (= [{:type :missing-source-map
                   :asset "js/broken.js"
                   :missing-path "js/missing.js.map"
                   :original-path "missing.js.map"}]
                 (:warnings processing-result)) "warns about missing source map"))))))
