;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets.process-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [hifi.assets.process :as process]))

(deftest resolve-path-test
  (testing "resolves relative paths correctly"
    (is (= "images/logo.png" (process/resolve-path "css" "../images/logo.png")))
    (is (= "css/components/button.css" (process/resolve-path "css" "./components/button.css")))
    (is (= "js/app.js" (process/resolve-path "css" "/js/app.js")))
    (is (= "css/main.css" (process/resolve-path "css" "main.css")))
    (is (= "logo.png" (process/resolve-path "." "../logo.png")))
    (is (= "nested/deep/file.js" (process/resolve-path "nested" "deep/file.js")))))
