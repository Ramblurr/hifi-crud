;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

(ns hifi.assets-test
  (:require
   [clojure.test :refer [deftest testing is are]]
   [hifi.assets :as assets]))

(deftest create-asset-context-test
  (testing "creates context with default configuration"
    (let [ctx (assets/create-asset-context)]
      (is (= #{:config :dev-mode? :manifest :manifest-path}
             (set (keys ctx))))
      (is (= false (:dev-mode? ctx)))
      (is (= ["assets"] (get-in ctx [:config ::assets/paths])))))

  (testing "creates context in dev mode"
    (is (= {:dev-mode? true :manifest nil}
           (select-keys (assets/create-asset-context {:dev-mode? true}) [:dev-mode? :manifest]))))

  (testing "creates context with custom config"
    (is (= ["custom/assets"]
           (get-in (assets/create-asset-context {:config {::assets/paths ["custom/assets"]}})
                   [:config ::assets/paths])))))

(deftest asset-path-test
  (testing "returns logical path in dev mode"
    (let [ctx (assets/create-asset-context {:dev-mode? true})]
      (is (= "app.js" (assets/asset-path ctx "app.js")))
      (is (= "css/app.css" (assets/asset-path ctx "css/app.css")))))

  (testing "returns digested path in production"
    (let [ctx (assoc (assets/create-asset-context {:dev-mode? false})
                     :manifest {"app.js" {:digest-path "app-abc123.js"}
                                "css/app.css" {:digest-path "css/app-def456.css"}})]
      (is (= "app-abc123.js" (assets/asset-path ctx "app.js")))
      (is (= "css/app-def456.css" (assets/asset-path ctx "css/app.css")))))

  (testing "returns logical path when not in manifest"
    (let [ctx (assets/create-asset-context {:dev-mode? false})]
      (is (= "missing.js" (assets/asset-path ctx "missing.js"))))))

(deftest asset-integrity-test
  (testing "returns nil in dev mode"
    (let [ctx (assets/create-asset-context {:dev-mode? true})]
      (is (nil? (assets/asset-integrity ctx "app.js")))))

  (testing "returns integrity hash in production"
    (let [ctx (assoc (assets/create-asset-context {:dev-mode? false})
                     :manifest {"app.js" {:integrity "sha384-ABC123"}})]
      (is (= "sha384-ABC123" (assets/asset-integrity ctx "app.js")))))

  (testing "returns nil when not in manifest"
    (let [ctx (assets/create-asset-context {:dev-mode? false})]
      (is (nil? (assets/asset-integrity ctx "missing.js"))))))

(deftest html-helpers-test
  (testing "stylesheet-link-tag"
    (let [ctx (assets/create-asset-context {:dev-mode? true})]
      (is (= [:link {:rel "stylesheet" :type "text/css" :href "app.css" :media "all"}]
             (assets/stylesheet-link-tag ctx "app.css")))

      (is (= [:link {:rel "stylesheet" :type "text/css" :href "app.css" :media "screen" :class "main-css"}]
             (assets/stylesheet-link-tag ctx "app.css" {:media "screen" :class "main-css"})))

      (let [prod-ctx (assoc (assets/create-asset-context {:dev-mode? false})
                            :manifest {"app.css" {:digest-path "app-abc123.css"
                                                  :integrity "sha384-XYZ"}})]
        (is (= [:link {:rel "stylesheet" :type "text/css" :href "app-abc123.css" :media "all" :integrity "sha384-XYZ"}]
               (assets/stylesheet-link-tag prod-ctx "app.css" {:integrity true}))))))

  (testing "script-tag"
    (let [ctx (assets/create-asset-context {:dev-mode? true})]
      (is (= [:script {:src "app.js" :type "text/javascript"}]
             (assets/script-tag ctx "app.js")))

      (is (= [:script {:src "app.js" :type "text/javascript" :async true :defer true}]
             (assets/script-tag ctx "app.js" {:async true :defer true})))

      (is (= [:script {:src "app.mjs" :type "module"}]
             (assets/script-tag ctx "app.mjs" {:type "module"})))))

  (testing "image-tag"
    (let [ctx (assets/create-asset-context {:dev-mode? true})]
      (is (= [:img {:src "logo.png"}]
             (assets/image-tag ctx "logo.png")))

      (is (= [:img {:src "logo.png" :alt "Company Logo" :width "100" :height "50"}]
             (assets/image-tag ctx "logo.png" {:alt "Company Logo" :width "100" :height "50"})))))

  (testing "preload-link-tag"
    (let [ctx (assets/create-asset-context {:dev-mode? true})]
      (is (= [:link {:rel "preload" :href "font.woff2" :as "font" :type "font/woff2"}]
             (assets/preload-link-tag ctx "font.woff2" {:as "font" :type "font/woff2"})))))

  (testing "batch helpers"
    (let [ctx (assets/create-asset-context {:dev-mode? true})]
      (is (= [[:script {:src "app.js" :type "text/javascript"}]
              [:script {:src "vendor.js" :type "text/javascript"}]]
             (assets/javascript-include-tags ctx ["app.js" "vendor.js"])))

      (is (= [[:link {:rel "stylesheet" :type "text/css" :href "app.css" :media "all"}]
              [:link {:rel "stylesheet" :type "text/css" :href "vendor.css" :media "all"}]]
             (assets/stylesheet-link-tags ctx ["app.css" "vendor.css"]))))))
