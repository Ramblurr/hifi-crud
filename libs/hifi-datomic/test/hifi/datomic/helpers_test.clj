(ns hifi.datomic.helpers-test
  (:require
   [datomic.api :as d]
   [hifi.datomic.core-test :refer [connect! test-fixture]]
   [hifi.datomic.fixtures.movies :as fixtures.movies]
   [hifi.datomic.helpers :as sut]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :each test-fixture)

(deftest about-one
  (let [db        (d/db (connect!))
        lenin-ent (d/entity db [:actor/id (-> fixtures.movies/ids :actor-1)])]
    (tap> [:db db])
    (testing "returns an entity given an id if it matches"
      (is (= lenin-ent
             (sut/one db [:actor/name "Lenin"]))))

    (testing "can accept multiple a/v pairs"
      (is (= lenin-ent
             (sut/one db [:actor/name "Lenin"] [:actor/favorite-color "Red"]))))

    (testing "works with just an attribute"
      (is (= lenin-ent
             (sut/one db :actor/favorite-color))))

    (testing "returns nil if nothing matches"
      (is (= nil
             (sut/one db [:actor/name "does not exist"]))))))

(deftest about-all
  (let [db (d/db (connect!))]
    (testing "returns ents"
      (is (= (map #(d/entity db [:movie/id %]) (vals (select-keys fixtures.movies/ids [:movie-1 :movie-2])))
             (sut/all db [:movie/id]))))

    (testing "works with just an attribute"
      (is (= (map #(d/entity db [:city/id %]) (vals (select-keys fixtures.movies/ids [:city-1 :city-2])))
             (sut/all db :city/name))))

    (testing "and you can provide an attribute value of course"
      (is (= (list (d/entity db [:actor/id (-> fixtures.movies/ids :actor-1)]))
             (sut/all db [:actor/name "Lenin"]))))

    (testing "returns empty seq if no results"
      (is (empty? (sut/all db [:actor/name "does not exist"]))))))

(deftest pull-one
  (let [db (d/db (connect!))]
    (testing "instead of returning the entity, pulls the attributes"
      (is (= {:actor/name "Lenin" :actor/favorite-color "Red"}
             (sut/pull-one db '[:actor/name :actor/favorite-color] [:actor/name "Lenin"]))))))

(deftest pull-all
  (let [db (d/db (connect!))]
    (testing "instead of returning the entity, pulls the attributes"
      (is (= (mapv #(select-keys % [:city/name]) (vals (select-keys fixtures.movies/data [:city-1 :city-2])))
             (sut/pull-all db '[:city/name] :city/name))))))
