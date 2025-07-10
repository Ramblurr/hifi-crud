;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
;; NOTE: This file is data and used in the generator
(ns build
  (:require
   [hifi.dev-tasks.config :as config]
   [clojure.tools.build.api :as b]
   [hifi.dev-tasks.css :as css]))

(def project (config/project-meta))
(def lib (:name project))
(def version (:version project))
(def main-ns (:main-ns project))

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"})
  (b/delete {:path "result"}))

(defn css [_]
  (css/build))

(defn js [_])

(defn uber [_]
  (clean nil)
  (css nil)
  (js nil)
  (b/copy-dir {:src-dirs   ["src" "resources" "target/resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis     basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     basis
           :main-ns   main-ns}))
