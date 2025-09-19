;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets-test
  (:require
   [clojure.test :refer [deftest testing is are]]
   [clojure.java.io :as io]
   [hifi.assets :as assets]))

(deftest create-asset-context-test
  (testing "creates context with default configuration"
    (let [ctx (assets/create-asset-context)]
      (is (= #{:config :manifest :manifest-path}
             (set (keys ctx))))
      (is (= {} (:manifest ctx)))
      (is (= ["assets"] (get-in ctx [:config ::assets/paths])))))

  (testing "creates context with custom config"
    (is (= ["custom/assets"]
           (get-in (assets/create-asset-context {:config {::assets/paths ["custom/assets"]}})
                   [:config ::assets/paths])))))

(deftest asset-path-test
  (testing "returns digested path when in manifest"
    (let [ctx (assoc (assets/create-asset-context)
                     :manifest {"app.js" {:digest-path "app-abc123.js"}
                                "css/app.css" {:digest-path "css/app-def456.css"}})]
      (is (= "app-abc123.js" (assets/asset-path ctx "app.js")))
      (is (= "css/app-def456.css" (assets/asset-path ctx "css/app.css")))))

  (testing "returns logical path when not in manifest"
    (let [ctx (assets/create-asset-context)]
      (is (= "missing.js" (assets/asset-path ctx "missing.js"))))))

(deftest asset-integrity-test
  (testing "returns integrity hash when in manifest"
    (let [ctx (assoc (assets/create-asset-context)
                     :manifest {"app.js" {:integrity "sha384-ABC123"}})]
      (is (= "sha384-ABC123" (assets/asset-integrity ctx "app.js")))))

  (testing "returns nil when not in manifest"
    (let [ctx (assets/create-asset-context)]
      (is (nil? (assets/asset-integrity ctx "missing.js"))))))

(deftest asset-read-test
  (testing "returns InputStream when asset exists in manifest and on disk"
    (let [temp-dir (str (System/getProperty "java.io.tmpdir") "/hifi-test-" (System/currentTimeMillis))
          output-dir (str temp-dir "/output")
          asset-file (str output-dir "/app-abc123.js")]
      (try
        (io/make-parents asset-file)
        (spit asset-file "console.log('test');")

        (let [ctx (assoc (assets/create-asset-context)
                         :config {:hifi.assets/output-dir output-dir}
                         :manifest {"app.js" {:digest-path "app-abc123.js"}})
              result (assets/asset-read ctx "app.js")]
          (is (some? result))
          (is (instance? java.io.InputStream result))
          (when result (.close result)))
        (finally
          (when (.exists (io/file asset-file))
            (.delete (io/file asset-file)))
          (when (.exists (io/file output-dir))
            (.delete (io/file output-dir)))
          (when (.exists (io/file temp-dir))
            (.delete (io/file temp-dir)))))))

  (testing "returns nil when asset not in manifest"
    (let [ctx (assets/create-asset-context)]
      (is (nil? (assets/asset-read ctx "missing.js")))))

  (testing "returns nil when asset in manifest but file doesn't exist"
    (let [ctx (assoc (assets/create-asset-context)
                     :config {:hifi.assets/output-dir "/nonexistent"}
                     :manifest {"app.js" {:digest-path "app-abc123.js"}})]
      (is (nil? (assets/asset-read ctx "app.js"))))))

(deftest asset-locate-test
  (testing "returns Path when asset exists in manifest and on disk"
    (let [temp-dir (str (System/getProperty "java.io.tmpdir") "/" (random-uuid))
          output-dir (str temp-dir "/output")
          asset-file (str output-dir "/app-abc123.js")]
      (try
        (io/make-parents asset-file)
        (spit asset-file "console.log('test');")

        (let [ctx (assoc (assets/create-asset-context)
                         :config {:hifi.assets/output-dir output-dir}
                         :manifest {"app.js" {:digest-path "app-abc123.js"}})
              path (assets/asset-locate ctx "app.js")]
          (is (some? path))
          (is (instance? java.nio.file.Path path))
          (is (= asset-file (str path))))
        (finally
          (when (.exists (io/file asset-file))
            (.delete (io/file asset-file)))
          (when (.exists (io/file output-dir))
            (.delete (io/file output-dir)))
          (when (.exists (io/file temp-dir))
            (.delete (io/file temp-dir)))))))

  (testing "returns nil when asset not in manifest"
    (let [ctx (assets/create-asset-context)]
      (is (nil? (assets/asset-locate ctx "missing.js")))))

  (testing "returns nil when asset in manifest but file doesn't exist"
    (let [ctx (assoc (assets/create-asset-context)
                     :config {:hifi.assets/output-dir "/nonexistent"}
                     :manifest {"app.js" {:digest-path "app-abc123.js"}})]
      (is (nil? (assets/asset-locate ctx "app.js"))))))
