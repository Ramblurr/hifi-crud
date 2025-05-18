(ns hifi.datomic.migrations-test
  (:require [hifi.datomic.migrations :as sut]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [datomic.api :as d]))

(def schema-db-uri "datomic:mem:/schema-test")

(defn create-db! []
  (d/create-database schema-db-uri))

(defn connect! []
  (d/connect schema-db-uri))

(defn erase-db! []
  (d/delete-database schema-db-uri))

(defn test-fixture [f]
  (create-db!)
  (f)
  (erase-db!))

(use-fixtures :each test-fixture)

;; Define a simple schema with a few attributes
(def test-schema-person
  {:id      :test-schema-person
   :tx-data [{:db/ident       :person/id
              :db/valueType   :db.type/uuid
              :db/cardinality :db.cardinality/one
              :db/unique      :db.unique/identity
              :db/doc         "Person's unique identifier"}

             {:db/ident       :person/name
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc         "Person's full name"}

             {:db/ident       :person/email
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one
              :db/unique      :db.unique/value
              :db/doc         "Person's email address"}]})

;; Define address schema directly
(def test-schema-address
  {:id :test-schema-address
   :tx-data [{:db/ident :address/id
              :db/valueType :db.type/uuid
              :db/cardinality :db.cardinality/one
              :db/unique :db.unique/identity
              :db/doc "Address ID"}

             {:db/ident :address/street
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "Street address"}

             {:db/ident :address/city
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "City name"}

             {:db/ident :person/address
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "Person's primary address"}]})

(defn schema-attributes-exist? [db]
  (let [attrs [:person/id :person/name :person/email
               :address/id :address/street :address/city :person/address]
        results (d/q '[:find ?ident
                       :in $ [?ident ...]
                       :where [_ :db/ident ?ident]]
                     db attrs)]
    (= (count results) (count attrs))))

(defn verify-schema-attribute [db attr-ident expected-type expected-cardinality]
  (let [result (d/q '[:find ?valueType ?cardinality
                      :in $ ?attr
                      :where
                      [?e :db/ident ?attr]
                      [?e :db/valueType ?valueType]
                      [?e :db/cardinality ?cardinality]]
                    db attr-ident)
        [value-type cardinality] (first result)]
    (and (= value-type expected-type)
         (= cardinality expected-cardinality))))

(deftest test-install-schema
  (testing "Installing schema data directly"
    (let [conn       (connect!)
          migrations [test-schema-person]
          _          (sut/install-schema conn migrations)
          db         (d/db conn)]

      ;; Check each attribute was installed correctly
      (is (d/entity db :person/id) "person/id attribute should exist")
      (is (d/entity db :person/name) "person/name attribute should exist")
      (is (d/entity db :person/email) "person/email attribute should exist")

      ;; Check attribute types
      (let [person-id-ent (d/entity db :person/id)]
        (is (= :db.type/uuid (:db/valueType person-id-ent)))
        (is (= :db.cardinality/one (:db/cardinality person-id-ent))))))

  (testing "Installing another schema separately"
    (let [conn       (connect!)
          migrations [test-schema-address]
          _          (sut/install-schema conn migrations)
          db         (d/db conn)]

      ;; Check each attribute was installed correctly
      (is (d/entity db :address/id) "address/id attribute should exist")
      (is (d/entity db :address/street) "address/street attribute should exist")
      (is (d/entity db :address/city) "address/city attribute should exist")
      (is (d/entity db :person/address) "person/address attribute should exist")

      ;; Check a specific attribute's type
      (let [addr-id-ent (d/entity db :address/id)]
        (is (= :db.type/uuid (:db/valueType addr-id-ent)))
        (is (= :db.cardinality/one (:db/cardinality addr-id-ent))))))

  (testing "Installing multiple schemas ensures all attributes exist"
    (let [conn       (connect!)
          migrations [test-schema-person test-schema-address]
          _          (sut/install-schema conn migrations)
          db         (d/db conn)]

      ;; Check for person attributes
      (is (d/entity db :person/id))
      (is (d/entity db :person/name))
      (is (d/entity db :person/email))

      ;; Check for address attributes
      (is (d/entity db :address/id))
      (is (d/entity db :address/street))
      (is (d/entity db :address/city))
      (is (d/entity db :person/address))))

  (testing "Installing the same schema twice is idempotent"
    (let [conn           (connect!)
          migrations     [test-schema-person]
          _              (sut/install-schema conn migrations)
          _              (sut/install-schema conn migrations)
          db             (d/db conn)
          expected-attrs #{:person/id :person/name :person/email}
          result-attrs   (d/q '[:find ?attr
                                :where [?e :db/ident ?attr]
                                [(namespace ?attr) ?ns]
                                [(= ?ns "person")]]
                              db)
          actual-attrs   (into #{} (map first result-attrs))]

      ;; Check that each expected attribute exists
      (doseq [attr expected-attrs]
        (is (contains? actual-attrs attr)
            (str attr " should exist after schema installation")))))

  (testing "Installing schema from a file path"
    (let [conn        (connect!)
          schema-path "hifi/datomic/fixtures/project-schema.edn"
          migrations  [schema-path]
          _           (sut/install-schema conn migrations)
          db          (d/db conn)]

      ;; Check each attribute was installed correctly
      (is (d/entity db :project/id) "project/id attribute should exist")
      (is (d/entity db :project/name) "project/name attribute should exist")
      (is (d/entity db :project/description) "project/description attribute should exist")
      (is (d/entity db :project/owner) "project/owner attribute should exist")
      (is (d/entity db :project/members) "project/members attribute should exist")

      ;; Verify cardinality of specific attributes
      (let [project-id-ent      (d/entity db :project/id)
            project-members-ent (d/entity db :project/members)]
        (is (= :db.cardinality/one (:db/cardinality project-id-ent))
            "project/id should have cardinality one")
        (is (= :db.cardinality/many (:db/cardinality project-members-ent))
            "project/members should have cardinality many"))))

  (testing "Installing schemas from both direct data and file path"
    (let [conn        (connect!)
          schema-path "hifi/datomic/fixtures/project-schema.edn"
          migrations  [test-schema-person schema-path]
          _           (sut/install-schema conn migrations)
          db          (d/db conn)]

      (is (d/entity db :person/id))
      (is (d/entity db :person/name))
      (is (d/entity db :person/email))

      (is (d/entity db :project/id))
      (is (d/entity db :project/name))
      (is (d/entity db :project/description))

      ;; Verify a relationship between schemas
      (let [project-owner-ent (d/entity db :project/owner)]
        (is (= :db.type/ref (:db/valueType project-owner-ent))
            "project/owner should be a reference type")))))
