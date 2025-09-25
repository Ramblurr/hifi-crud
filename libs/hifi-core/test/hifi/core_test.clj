(ns hifi.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [hifi.core :as h]))

(def routes-form
  '(hifi.core/defroutes sample-routes
     ["/api" {:middleware [:mw]}
      ["/ping" {:name :ping
                :get {:handler (fn [_] :pong)}}]
      ["/health" ["/sub" {:get {:handler (constantly :ok)}}]]]))

(defn- evaluate-routes [dev?]
  (let [current-ns (the-ns 'hifi.core-test)
        expanded (binding [*ns* current-ns]
                   (macroexpand routes-form))
        [_def _name & body] expanded
        value-expr (first (if (string? (first body))
                            (rest body)
                            body))]
    (with-redefs [h/dev? (constantly dev?)]
      (eval value-expr))))

(deftest defroutes-annotates-when-dev
  (let [route-def (evaluate-routes true)
        routes (:routes route-def)
        root-data (second routes)
        ping-route (nth routes 2)
        ping-data (second ping-route)
        health-route (nth routes 3)
        health-data (second health-route)
        health-child (nth health-route 2)]
    (is (= ::sample-routes (:route-name route-def)))
    (testing "top-level route retains original data and annotation"
      (is (= [:mw] (:middleware root-data)))
      (is (= 'hifi.core-test (get-in root-data [:hifi/annotation :ns])))
      (is (pos-int? (get-in root-data [:hifi/annotation :line]))))
    (testing "child routes are annotated"
      (is (= :ping (:name ping-data)))
      (is (= 'hifi.core-test (get-in ping-data [:hifi/annotation :ns])))
      (is (pos-int? (get-in ping-data [:hifi/annotation :line]))))
    (testing "routes without data maps receive one for annotation"
      (is (= 'hifi.core-test (get-in health-data [:hifi/annotation :ns])))
      (is (pos-int? (get-in health-data [:hifi/annotation :line]))))
    (testing "child routes remain intact"
      (is (= "/sub" (first health-child)))
      (is (fn? (get-in (second health-child) [:get :handler]))))))

(deftest defroutes-leaves-data-unchanged-when-not-dev
  (let [route-def (evaluate-routes false)
        routes (:routes route-def)
        root-data (second routes)
        ping-route (nth routes 2)
        ping-data (second ping-route)
        health-route (nth routes 3)
        health-child (nth health-route 1)]
    (is (= ::sample-routes (:route-name route-def)))
    (testing "top-level route data untouched"
      (is (= [:mw] (:middleware root-data)))
      (is (nil? (:hifi/annotation root-data))))
    (testing "child route maps unchanged"
      (is (= :ping (:name ping-data)))
      (is (nil? (:hifi/annotation ping-data))))
    (testing "routes without maps stay unchanged"
      (is (= "/health" (first health-route)))
      (is (= "/sub" (ffirst (rest health-route)))))
    (testing "child route unaffected"
      (is (= "/sub" (first health-child)))
      (is (fn? (get-in (second health-child) [:get :handler]))))))

#_{:clj-kondo/ignore [:inline-def]}
(deftest defplugin-creates-valid-plugin
  (hifi.core/defplugin test-plugin "Test plugin" {hifi.core/system-defaults {}})
  (is (= {:donut.system.plugin/name :hifi.core-test/test-plugin,
          :donut.system/doc "Test plugin",
          :donut.system.plugin/system-defaults
          {:donut.system/defs {:donut.system.plugin/system-defaults {}}}}
         test-plugin)))

#_{:clj-kondo/ignore [:inline-def]}
(deftest defcomponent-creates-valid-component
  (hifi.core/defcomponent test-comp "Test component" {:donut.system/start constantly})
  (is (= {:donut.system/name :hifi.core-test/test-comp,
          :donut.system/doc "Test component",
          :donut.system/start constantly}
         test-comp)))
