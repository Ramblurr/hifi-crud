;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

(ns hifi.html.impl-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [hifi.html.impl :as impl]
   [hifi.html.protocols :as p]))

(deftest collect-link-test
  (testing "collects preload from link element"
    (is (= {:path "/css/app.css" :rel "preload" :as "style"}
           (impl/collect-link [:link {:href "/css/app.css" :rel "preload" :as "style"} nil]))))

  (testing "collects preload from script element"
    (is (= {:path "/app.js" :rel "preload" :as "script"}
           (impl/collect-link [:script {:href "/app.js"} nil]))))

  (testing "returns nil for non-preloadable elements"
    (is (= nil
           (impl/collect-link [:link {:href "/css/app.css" :rel "stylesheet"} nil])))))

(deftest collect-head-preloads-xf-test
  (testing "extracts preloads using transducer"
    (is (= [{:path "/css/app.css" :rel "preload" :as "style"}
            {:path "/app.js" :rel "preload" :as "script"}]
           (transduce impl/collect-head-preloads-xf
                      conj
                      []
                      [[:head {}
                        [:link {:href "/css/app.css" :rel "preload" :as "style"}]
                        [:script {:href "/app.js"}]]]))))

  (testing "handles multiple hiccup documents"
    (is (= [{:path "/a.css" :rel "preload" :as "style"}
            {:path "/b.js" :rel "preload" :as "script"}]
           (transduce impl/collect-head-preloads-xf
                      conj
                      []
                      [[:head {} [:link {:href "/a.css" :rel "preload" :as "style"}]]
                       [:head {} [:script {:href "/b.js"}]]]))))

  (testing "filters out non-head elements"
    (is (= []
           (transduce impl/collect-head-preloads-xf
                      conj
                      []
                      [[:body {} [:link {:href "/app.css" :rel "preload" :as "style"}]]])))))

(deftest has-asset-marker?-test
  (testing "detects asset marker metadata"
    (is (= true
           (impl/has-asset-marker? (with-meta [:link {:href "/app.css"}]
                                     {:hifi.html/asset-marker true})))))

  (testing "returns false for non-marked elements"
    (is (= false
           (impl/has-asset-marker? [:link {:href "/app.css"}])))))

(deftest preloads->header-test
  (testing "formats preloads with attributes"
    (is (= "</css/app.css>; rel=preload; as=style; type=\"text/css\""
           (impl/preloads->header [{:path "/css/app.css" :rel "preload" :as "style" :type "text/css"}] {}))))

  (testing "joins multiple preloads"
    (is (= "</a.css>; rel=preload; as=style, </b.js>; rel=modulepreload"
           (impl/preloads->header [{:path "/a.css" :rel "preload" :as "style"}
                                   {:path "/b.js" :rel "modulepreload"}] {}))))

  (testing "returns nil for empty"
    (is (= nil
           (impl/preloads->header [] {})))))

(defrecord MockAssetResolver [assets]
  p/AssetResolver
  (resolve-path [_ logical-path]
    (get-in assets [logical-path :resolved-path]))
  (integrity [_ logical-path]
    (get-in assets [logical-path :integrity]))
  (read-bytes [_ _logical-path]
    nil)
  (locate [_ _logical-path]
    nil))

(deftest resolve-preloads-test
  (let [resolver (->MockAssetResolver
                  {"app.css" {:resolved-path "/assets/app-abc123.css"
                              :integrity "sha256-abc123"}
                   "app.js" {:resolved-path "/assets/app-xyz789.js"
                             :integrity nil}})]

    (testing "extracts and resolves preloads from hiccup"
      (is (= [{:path "/assets/app-abc123.css" :rel "preload" :as "style" :integrity "sha256-abc123"}
              {:path "/assets/app-xyz789.js" :rel "preload" :as "script"}]
             (impl/resolve-preloads resolver
                                    [:head {}
                                     [:link {:href "app.css" :rel "preload" :as "style"}]
                                     [:script {:href "app.js"}]]))))

    (testing "filters out unresolvable assets"
      (is (= [{:path "/assets/app-abc123.css" :rel "preload" :as "style" :integrity "sha256-abc123"}]
             (impl/resolve-preloads resolver
                                    [:head {}
                                     [:link {:href "app.css" :rel "preload" :as "style"}]
                                     [:link {:href "missing.css" :rel "preload" :as "style"}]]))))

    (testing "returns empty when no resolver"
      (is (= []
             (impl/resolve-preloads nil
                                    [:head {}
                                     [:link {:href "app.css" :rel "preload" :as "style"}]]))))))
