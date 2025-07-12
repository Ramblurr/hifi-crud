;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2 

 (ns hifi.assets.digest-test
   (:require
    [clojure.test :refer [deftest testing is are]]
    [hifi.assets.digest :as digest]))

(deftest extract-existing-digest-test
  (testing "extracts digest from pre-digested filename"
    (are [filename expected] (= expected (digest/extract-existing-digest filename))
      "app-abc12345.digested.js" ["app" "abc12345" "js"]
      "style-def67890.digested.css" ["style" "def67890" "css"]
      "vendor.bundle-12345678.digested.js" ["vendor.bundle" "12345678" "js"]))

  (testing "returns nil for non-digested filenames"
    (are [filename] (nil? (digest/extract-existing-digest filename))
      "app.js"
      "style.css"
      "app-abc123.js"
      "digested.js")))

(deftest is-pre-digested?-test
  (testing "identifies pre-digested files"
    (are [filename expected] (= expected (digest/is-pre-digested? filename))
      "app-abc12345.digested.js" true
      "style-def67890.digested.css" true
      "app.js" false
      "app-abc123.js" false
      "not-a-match.digested" false)))

(deftest create-digested-filename-test
  (testing "creates digested filename with 8-char prefix"
    (are [filename digest expected] (= expected (digest/create-digested-filename filename digest))
      "app.js" "abc123def456789" "app-abc123de.js"
      "style.css" "123456789abcdef" "style-12345678.css"
      "no-extension" "abcdefghijklmno" "no-extension-abcdefgh"
      "multiple.dots.file.js" "xyz123456789" "multiple.dots.file-xyz12345.js")))

(deftest short-digest-test
  (testing "returns first 8 characters"
    (are [full short] (= short (digest/short-digest full))
      "abc123def456789" "abc123de"
      "123456789abcdef" "12345678"
;; when less than 8 chars, would throw - but shouldn't happen with real digests
      )))