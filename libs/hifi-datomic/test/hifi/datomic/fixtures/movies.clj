;; sourced from https://github.com/majorcluster/datomic-helper
;; MIT License
;; Copyright (c) 2023 Major Cluster
;;
;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:
;;
;; The above copyright notice and this permission notice shall be included in all
;; copies or substantial portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.
(ns hifi.datomic.fixtures.movies
  (:require [clojure.test :refer :all]
            [datomic.api :as d])
  (:import [java.util UUID]))

(def specs
  [#:db{:ident :movie/id, :cardinality :db.cardinality/one, :valueType :db.type/uuid, :unique :db.unique/identity}
   #:db{:ident :movie/name, :cardinality :db.cardinality/one, :valueType :db.type/string}
   #:db{:ident :movie/year, :cardinality :db.cardinality/one, :valueType :db.type/long}
   #:db{:ident :actor/id, :cardinality :db.cardinality/one, :valueType :db.type/uuid, :unique :db.unique/identity}
   #:db{:ident :actor/name, :cardinality :db.cardinality/one, :valueType :db.type/string}
   #:db{:ident :actor/favorite-color, :cardinality :db.cardinality/one, :valueType :db.type/string}
   #:db{:ident :movie/actors, :cardinality :db.cardinality/many, :valueType :db.type/ref, :isComponent true}
   #:db{:ident :city/id, :cardinality :db.cardinality/one, :valueType :db.type/uuid, :unique :db.unique/identity}
   #:db{:ident :city/name, :cardinality :db.cardinality/one, :valueType :db.type/string}
   #:db{:ident :actor/city, :cardinality :db.cardinality/one, :valueType :db.type/ref}
   #:db{:ident :empty-datom/id, :cardinality :db.cardinality/one, :valueType :db.type/uuid, :unique :db.unique/identity}])

(def ids
  {:city-1  #uuid "f5fc7aef-8914-48f7-a95f-09e407c159f2"
   :city-2  #uuid "b3982a37-4cfa-4d2c-afa7-946d5b5809fb"
   :movie-1 #uuid "90b8c642-9ac3-4a82-bb89-144a403bf233"
   :movie-2 #uuid "57523ff3-04f8-45f4-a039-316837eb1f39"
   :actor-1 #uuid "aba4c51f-4a2b-4b37-bebe-6f79f70890a9"
   :actor-2 #uuid "6c355a88-82cb-420e-ad98-e4b3dc470fe4"
   :actor-3 #uuid "691907ce-a156-4ab8-87a0-d963408cece1"})

(def data
  {:city-1  {:city/name "Leningrad", :city/id (:city-1 ids)}
   :city-2  {:city/name "Havana", :city/id (:city-2 ids)}
   :movie-1 {:movie/name "1917" :movie/id (:movie-1 ids) :movie/year 1930}
   :movie-2 {:movie/name "Sitio de la isla" :movie/id (:movie-2 ids) :movie/year 1977}
   :actor-1 {:actor/name "Lenin" :actor/favorite-color "Red" :actor/city [:city/id (:city-1 ids)] :actor/id (:actor-1 ids)}
   :actor-2 {:actor/name "Celia Sanchez" :actor/city [:city/id (:city-2 ids)] :actor/id (:actor-2 ids)}
   :actor-3 {:actor/name "El Fidel" :actor/city [:city/id (:city-2 ids)] :actor/id (:actor-3 ids)}})

(defn insert-samples
  [conn]
  (d/transact conn
              [(:city-1 data) (:city-2 data)])
  (d/transact conn
              [(:movie-1 data) (:movie-2 data)])
  (d/transact conn
              [(:actor-1 data) (:actor-2 data) (:actor-3 data)])
  (d/transact conn
              [[:db/add [:movie/id (:movie-1 ids)]
                :movie/actors [:actor/id (:actor-1 ids)]]
               [:db/add [:movie/id (:movie-2 ids)]
                :movie/actors [:actor/id (:actor-2 ids)]]
               [:db/add [:movie/id (:movie-2 ids)]
                :movie/actors [:actor/id (:actor-3 ids)]]]))
