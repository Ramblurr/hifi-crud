;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.assets.middleware-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest testing is]]
   [hifi.assets.middleware :as middleware])
  (:import
   (java.io InputStream)))

(defn normalize-response [response]
  (-> response
      (update :body (fn [body]
                      (cond
                        (nil? body) nil
                        (instance? InputStream body)
                        (with-open [stream body]
                          (slurp stream))
                        :else body)))))

(deftest assets-middleware-behavior-test
  (let [temp-dir (fs/create-temp-dir)]
    (try
      (let [output-dir        (fs/path temp-dir "public" "assets")
            output-dir-str    (str output-dir)
            _                 (fs/create-dirs (fs/path output-dir "css"))
            content           "body {}"
            digest-path       "css/app-abcdef12.css"
            logical-path      "css/app.css"
            asset-path        (fs/path output-dir digest-path)
            _                 (spit (str asset-path) content)
            manifest          {logical-path {:digest-path   digest-path
                                             :size          (count content)
                                             :last-modified "2025-01-01T00:00:00Z"}}
            asset-ctx         {:config   {:hifi.assets/output-dir output-dir-str
                                          :hifi.assets/prefix     "/assets"}
                               :manifest manifest}
            middleware        (middleware/assets-middleware {:asset-ctx asset-ctx})
            handler-count     (atom 0)
            handler           (fn [_]
                                (swap! handler-count inc)
                                {:status 599
                                 :headers {}
                                 :body    "handler"})]
        (testing "serves digested asset on GET"
          (reset! handler-count 0)
          (let [response (((:wrap middleware) handler)
                          {:request-method :get
                           :uri            "/assets/css/app-abcdef12.css"
                           :headers        {}})
                expected {:status 200
                          :headers {"Content-Type"   "text/css"
                                    "Cache-Control"  "public, max-age=31536000, immutable"
                                    "Vary"           "Accept-Encoding"
                                    "Content-Length" (str (count content))
                                    "ETag"           "\"abcdef12\""
                                    "Last-Modified"  "2025-01-01T00:00:00Z"}
                          :body    content}]
            (is (= expected (normalize-response response)))
            (is (= 0 @handler-count))))

        (testing "serves metadata on HEAD"
          (reset! handler-count 0)
          (let [response (((:wrap middleware) handler)
                          {:request-method :head
                           :uri            "/assets/css/app-abcdef12.css"
                           :headers        {}})
                expected {:status 200
                          :headers {"Content-Type"   "text/css"
                                    "Cache-Control"  "public, max-age=31536000, immutable"
                                    "Vary"           "Accept-Encoding"
                                    "Content-Length" (str (count content))
                                    "ETag"           "\"abcdef12\""
                                    "Last-Modified"  "2025-01-01T00:00:00Z"}
                          :body    nil}]
            (is (= expected (normalize-response response)))
            (is (= 0 @handler-count))))

        (testing "returns 304 when If-None-Match matches"
          (reset! handler-count 0)
          (let [response (((:wrap middleware) handler)
                          {:request-method :get
                           :uri            "/assets/css/app-abcdef12.css"
                           :headers        {"if-none-match" "\"abcdef12\""}})
                expected {:status 304
                          :headers {"Content-Type"  "text/css"
                                    "Cache-Control" "public, max-age=31536000, immutable"
                                    "Vary"          "Accept-Encoding"
                                    "ETag"          "\"abcdef12\""
                                    "Last-Modified" "2025-01-01T00:00:00Z"}
                          :body    nil}]
            (is (= expected (normalize-response response)))
            (is (= 0 @handler-count))))

        (testing "returns 404 when asset missing"
          (reset! handler-count 0)
          (let [response (((:wrap middleware) handler)
                          {:request-method :get
                           :uri            "/assets/css/missing-abcdef12.css"
                           :headers        {}})
                expected {:status 404
                          :headers {"Content-Type"   "text/plain"
                                    "Content-Length" "9"}
                          :body    "Not found"}]
            (is (= expected (normalize-response response)))
            (is (= 0 @handler-count))))

        (testing "passes through when method is not GET or HEAD"
          (reset! handler-count 0)
          (let [response (((:wrap middleware) handler)
                          {:request-method :post
                           :uri            "/assets/css/app-abcdef12.css"
                           :headers        {}})
                expected {:status 599
                          :headers {}
                          :body    "handler"}]
            (is (= expected (normalize-response response)))
            (is (= 1 @handler-count))))

        (testing "passes through when path does not match prefix"
          (reset! handler-count 0)
          (let [response (((:wrap middleware) handler)
                          {:request-method :get
                           :uri            "/other/app-abcdef12.css"
                           :headers        {}})
                expected {:status 599
                          :headers {}
                          :body    "handler"}]
            (is (= expected (normalize-response response)))
            (is (= 1 @handler-count)))))
      (finally
        (fs/delete-tree temp-dir)))))
