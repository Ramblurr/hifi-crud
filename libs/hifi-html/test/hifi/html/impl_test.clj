;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

(ns hifi.html.impl-test
  (:require

   [dev.onionpancakes.chassis.core :as chassis]
   [clojure.test :refer [deftest testing is]]
   [hifi.html.impl :as impl]))

(deftest collect-link-test
  (testing "collects preload from link element"
    (is (= {:path "/css/app.css" :rel "preload" :as "style"}
           (impl/collect-link [:link {:href "/css/app.css" :rel "preload" :as "style"} nil])))

    (is (= {:path "/font.woff2" :rel "preload" :as "font" :type "font/woff2"}
           (impl/collect-link [:link {:href "/font.woff2" :rel "preload" :as "font" :type "font/woff2"} nil])))

    (is (= {:path "/module.js" :rel "modulepreload"}
           (impl/collect-link [:link {:href "/module.js" :rel "modulepreload"} nil]))))

  (testing "collects preload with crossorigin and integrity"
    (is (= {:path "/css/app.css" :rel "preload" :as "style" :crossorigin "foobar" :integrity? true}
           (impl/collect-link [:link {:href "/css/app.css" :rel "preload" :as "style"
                                      :crossorigin "foobar" :integrity true} nil]))))

  (testing "collects preload from script element"
    (is (= {:path "/app.js" :rel "preload" :as "script"}
           (impl/collect-link [:script {:href "/app.js"} nil])))

    (is (= {:path "/app.js" :rel "preload" :as "script" :type "text/javascript"}
           (impl/collect-link [:script {:href "/app.js" :type "text/javascript"} nil])))

    (is (= {:path "/module.js" :rel "modulepreload"}
           (impl/collect-link [:script {:href "/module.js" :type "module"} nil]))))

  (testing "returns nil for non-preloadable elements"
    (is (= nil
           (impl/collect-link [:link {:href "/css/app.css" :rel "stylesheet"} nil])))

    (is (= nil
           (impl/collect-link [:div {:class "content"} nil])))

    (is (= nil
           (impl/collect-link [:link {} nil])))))

(deftest collect-head-preloads-test
  (testing "collects preloads from head element"
    (is (= [{:path "/css/app.css" :rel "preload" :as "style"}
            {:path "/app.js" :rel "preload" :as "script"}]
           (impl/collect-head-preloads
            [:head {}
             (with-meta [:link {:href "/css/app.css" :rel "preload" :as "style"}]
               {:hifi.html/asset-marker true})
             (with-meta [:script {:href "/app.js"}]
               {:hifi.html/asset-marker true})]))))

  (testing "collects multiple preloads with various attributes"
    (is (= [{:path "/font.woff2" :rel "preload" :as "font" :type "font/woff2"}
            {:path "/module.js" :rel "modulepreload"}
            {:path "/style.css" :rel "preload" :as "style" :crossorigin "anonymous" :integrity? true}]
           (impl/collect-head-preloads
            [:head {}
             (with-meta [:link {:href "/font.woff2" :rel "preload" :as "font" :type "font/woff2"}]
               {:hifi.html/asset-marker true})
             (with-meta [:script {:href "/module.js" :type "module"}]
               {:hifi.html/asset-marker true})
             (with-meta [:link {:href "/style.css" :rel "preload" :as "style"
                                :crossorigin "anonymous" :integrity true}]
               {:hifi.html/asset-marker true})]))))

  (testing "elements without asset marker"
    (is (=   [{:as "style" :path "/css/app.css" :rel "preload"}
              {:as "script" :path "/app.js" :rel "preload"}]
             (impl/collect-head-preloads
              [:head {}
               [:link {:href "/css/app.css" :rel "preload" :as "style"}]
               [:script {:href "/app.js"}]]))))

  (testing "ignores non-preloadable elements with asset marker"
    (is (= []
           (impl/collect-head-preloads
            [:head {}
             (with-meta [:link {:href "/css/app.css" :rel "stylesheet"}]
               {:hifi.html/asset-marker true})
             (with-meta [:meta {:name "viewport" :content "width=device-width"}]
               {:hifi.html/asset-marker true})]))))

  (testing "handles nested head structures"
    (is (= [{:path "/css/app.css" :rel "preload" :as "style"}
            {:path "/nested.js" :rel "preload" :as "script"}]
           (impl/collect-head-preloads
            [:head {}
             (with-meta [:link {:href "/css/app.css" :rel "preload" :as "style"}]
               {:hifi.html/asset-marker true})
             [:div {}
              (with-meta [:script {:href "/nested.js"}]
                {:hifi.html/asset-marker true})]]))))

  (testing "returns empty for missing head"
    (is (= []
           (impl/collect-head-preloads
            [:body {}
             (with-meta [:link {:href "/css/app.css" :rel "preload" :as "style"}]
               {:hifi.html/asset-marker true})]))))

  (testing "returns empty for non-html structure"
    (is (= []
           (impl/collect-head-preloads [:div {} [:span "text"]]))))

  (testing "works with chassis doctype structure"
    (is (= [{:path "/css/app.css" :rel "preload" :as "style"}
            {:as "script" :path "/nested.js" :rel "preload" :crossorigin "wow"}]
           (impl/collect-head-preloads
            [chassis/doctype-html5
             [:head {}
              ^:hifi.html/asset-marker [:link {:href "/css/app.css" :rel "preload" :as "style"}]
              ^:hifi.html/asset-marker [:script {:href "/nested.js" :crossorigin "wow" :id "myscript"}]]
             [:body [:h1 "Hello"]]])))))

(deftest has-asset-marker?-test
  (testing "detects asset marker metadata"
    (is (= true
           (impl/has-asset-marker? (with-meta [:link {:href "/app.css"}]
                                     {:hifi.html/asset-marker true}))))

    (is (= true
           (impl/has-asset-marker? (with-meta [:script {:src "/app.js"}]
                                     {:hifi.html/asset-marker true})))))

  (testing "returns false for non-marked elements"
    (is (= false
           (impl/has-asset-marker? [:link {:href "/app.css"}])))

    (is (= false
           (impl/has-asset-marker? "text")))

    (is (= false
           (impl/has-asset-marker? nil)))))

(deftest preloads->header-test
  (testing "formats preloads with all attributes"
    (is (= "</css/app.css>; rel=preload; as=style; type=\"text/css\"; crossorigin=anonymous; integrity=\"sha256-abc123\""
           (impl/preloads->header [{:path "/css/app.css" :rel "preload" :as "style"
                                    :type "text/css" :crossorigin "anonymous" :integrity "sha256-abc123"}] {}))))

  (testing "joins multiple preloads"
    (is (= "</a.css>; rel=preload; as=style, </b.js>; rel=modulepreload"
           (impl/preloads->header [{:path "/a.css" :rel "preload" :as "style"}
                                   {:path "/b.js" :rel "modulepreload"}] {}))))

  (testing "respects max size limit"
    (is (= "</a.css>; rel=preload; as=style"
           (impl/preloads->header [{:path "/a.css" :rel "preload" :as "style"}
                                   {:path "/very-long-file-name.css" :rel "preload" :as "style"}]
                                  {:max-size 35}))))

  (testing "returns nil for empty or oversized"
    (is (= nil
           (impl/preloads->header [] {})))
    (is (= nil
           (impl/preloads->header [{:path "/css/app.css" :rel "preload" :as "style"}]
                                  {:max-size 10})))))
