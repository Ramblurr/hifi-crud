;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.build
  (:require
   [babashka.fs :as fs]
   [babashka.tasks :refer [clojure]]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def build-default-src
  (->> (io/resource "hifi/dev_tasks/build_default.clj")
       slurp
       str/split-lines
       (drop-while #(str/starts-with? % ";;"))
       (str/join "\n")))

(defn build-clj-exists? []
  (fs/exists? (fs/file "build.clj")))

(defn generate-build-clj
  "Generate a build.clj file if it doesn't exist."
  []
  (when-not (build-clj-exists?)
    (spit "build.clj" build-default-src)
    (println "Generated build.clj from template.")))

(defn clean
  "Clean build artifacts"
  []
  (generate-build-clj)
  (clojure "-T:build clean"))

(defn uber
  "Build an uberjar"
  []
  (generate-build-clj)
  (clojure "-T:build uber"))
