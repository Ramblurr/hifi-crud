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
(ns hifi.datomic.core-test
  {:clj-kondo/ignore [:refer-all]}
  (:require [clojure.test :refer :all]
            [hifi.datomic.fixtures.movies :as fixtures.movies]
            [datomic.api :as d]))

(defonce db-uri "datomic:mem:/movies")

(defn create-db! []
  (d/create-database db-uri))

(defn connect! []
  (d/connect db-uri))

(defn create-schema!
  [conn]
  (d/transact conn fixtures.movies/specs))

(defn insert-schema-samples!
  [conn]
  (fixtures.movies/insert-samples conn))

(defn erase-db!
  "test use only!!!"
  []
  (println "ERASING DB!!!!!!!")
  (d/delete-database db-uri))

(defn start-db
  []
  (create-db!)
  (let [conn (connect!)]
    @(create-schema! conn)
    @(insert-schema-samples! conn)))
(defn setup
  []
  (start-db))

(defn teardown
  []
  (erase-db!))

(defn test-fixture [f]
  (setup)
  (f)
  (teardown))
