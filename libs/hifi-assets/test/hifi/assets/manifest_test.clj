;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

 (ns hifi.assets.manifest-test
   (:require
    [clojure.test :refer [deftest testing is]]
    [hifi.assets.manifest :as manifest]))

(deftest create-manifest-entry-test
  (testing "creates manifest entry from digest info"
    (let [digest-info {:digest-name "app-abc123.js"
                       :sri-hash "sha384-XYZ789"
                       :size 1024
                       :logical-path "app.js"}
          result (manifest/create-manifest-entry digest-info)]
      (is (= {:digest-path "app-abc123.js"
              :integrity "sha384-XYZ789"
              :size 1024}
             (dissoc (get result "app.js") :last-modified)))
      (is (string? (get-in result ["app.js" :last-modified]))))))

(deftest generate-manifest-test
  (testing "generates manifest from multiple digest infos"
    (let [digest-infos [{:digest-name "app-abc123.js"
                         :sri-hash "sha384-ABC"
                         :size 1024
                         :logical-path "app.js"}
                        {:digest-name "style-def456.css"
                         :sri-hash "sha384-DEF"
                         :size 2048
                         :logical-path "style.css"}]
          result (manifest/generate-manifest digest-infos)]
      (is (= {"app.js" "app-abc123.js"
              "style.css" "style-def456.css"}
             (update-vals result :digest-path))))))

(deftest manifest-lookup-test
  (let [manifest {"app.js" {:digest-path "app-abc123.js"}
                  "style.css" {:digest-path "style-def456.css"}}]
    (testing "returns digested path when found"
      (is (= "app-abc123.js" (manifest/manifest-lookup manifest "app.js"))))

    (testing "returns nil when not found"
      (is (nil? (manifest/manifest-lookup manifest "missing.js"))))))

(deftest manifest-integrity-test
  (let [manifest {"app.js" {:integrity "sha384-ABC123"}}]
    (testing "returns integrity hash when found"
      (is (= "sha384-ABC123" (manifest/manifest-integrity manifest "app.js"))))

    (testing "returns nil when not found"
      (is (nil? (manifest/manifest-integrity manifest "missing.js"))))))

(deftest manifest-contains?-test
  (let [manifest {"app.js" {:digest-path "app-abc123.js"}}]
    (testing "returns true when path exists"
      (is (manifest/manifest-contains? manifest "app.js")))

    (testing "returns false when path doesn't exist"
      (is (not (manifest/manifest-contains? manifest "missing.js"))))))

(deftest update-manifest-entry-test
  (testing "updates existing entry"
    (let [manifest {"app.js" {:digest-path "app-old.js"
                              :integrity "sha384-OLD"}}
          digest-info {:digest-name "app-new.js"
                       :sri-hash "sha384-NEW"
                       :size 2048
                       :logical-path "app.js"}
          result (manifest/update-manifest-entry manifest digest-info)]
      (is (= {:digest-path "app-new.js"
              :integrity "sha384-NEW"
              :size 2048}
             (dissoc (get result "app.js") :last-modified)))))

  (testing "adds new entry"
    (let [manifest {"app.js" {:digest-path "app-abc123.js"}}
          digest-info {:digest-name "style-def456.css"
                       :sri-hash "sha384-XYZ"
                       :size 1024
                       :logical-path "style.css"}
          result (manifest/update-manifest-entry manifest digest-info)]
      (is (= #{"app.js" "style.css"}
             (set (keys result)))))))
