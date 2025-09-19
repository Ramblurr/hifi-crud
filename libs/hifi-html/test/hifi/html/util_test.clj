(ns hifi.html.util-test
  (:require [clojure.test :refer [deftest is testing]]
            [hifi.html.util :refer [tree-seq-bfs hiccup-seq find-first-element]]))

(deftest tree-seq-bfs-test
  (testing "tree-seq-bfs with simple tree structure"
    (let [tree   [:a [:b :c] [:d [:e :f]]]
          result (tree-seq-bfs sequential? seq tree)]
      (is (= [[:a [:b :c] [:d [:e :f]]] :a [:b :c] [:d [:e :f]] :b :c :d [:e :f] :e :f]
             result))))

  (testing "tree-seq-bfs with single node"
    (is (= [:single] (tree-seq-bfs sequential? seq :single))))

  (testing "empty coll returns a sequence containing the empty coll itself"
    (is (= [[]] (tree-seq-bfs sequential? seq [])))))

(deftest hiccup-seq-test
  (testing "empty coll returns a sequence containing the empty coll itself"
    (is (= [[]] (hiccup-seq []))))

  (testing "hiccup-seq with nested elements"
    (let [hiccup [:html
                  [:head [:title "Page"]]
                  [:body
                   [:div {:id "main"}
                    [:h1 "Header"]
                    [:div [:p "Paragraph"]]]]]
          result (hiccup-seq hiccup)]
      (is (= [[:html [:head [:title "Page"]] [:body [:div {:id "main"} [:h1 "Header"] [:div [:p "Paragraph"]]]]]
              [:head [:title "Page"]]
              [:body [:div {:id "main"} [:h1 "Header"] [:div [:p "Paragraph"]]]]
              [:title "Page"]
              [:div {:id "main"} [:h1 "Header"] [:div [:p "Paragraph"]]]
              [:h1 "Header"]
              [:div [:p "Paragraph"]]
              [:p "Paragraph"]]
             result))))

  (testing "hiccup-seq with single element"
    (let [hiccup [:br]
          result (hiccup-seq hiccup)]
      (is (= [[:br]] result))))

  (testing "hiccup-seq with list of elements"
    (let [hiccup '([:div "one"] [:div "two"])
          result (hiccup-seq hiccup)]
      (is (= [[:div "one"] [:div "two"]] result)))))

(deftest find-first-element-test
  (testing "find-first-element with nested structure"
    (let [hiccup [:html
                  [:head [:title "Page"]]
                  [:body
                   [:div {:id "main"}
                    [:h1 "Header"]
                    [:div [:p {:class "target"} "Found me"]]]]]
          result (find-first-element #(= "target" (get-in % [1 :class])) hiccup)]
      (is (= [:p {:class "target"} "Found me"] result)))))
