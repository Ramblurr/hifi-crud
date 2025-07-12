(ns make
  (:refer-clojure :exclude [test])
  (:require
   [clojure.pprint :as pp]
   [clojure.edn :as edn]
   [babashka.deps :as deps]
   [babashka.fs :as fs]
   [babashka.process :as p]
   [babashka.tasks :refer [clojure]]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn ^:private hifi-libs []
  (->> (slurp "deps.edn")
       edn/read-string
       :aliases
       :dev
       :extra-deps
       (filter #(-> % key str (.startsWith "hifi/")))
       (mapv (fn [[k v]]
               (assoc v :lib k
                      :test-paths [(str (:local/root v) "/test")]
                      :resource-paths [(str (:local/root v) "/resources")]
                      :src-paths [(str (:local/root v) "/src")])))))

(defn ^:private kaocha-config [libs]
  {:kaocha/source-paths (->> libs
                             (map :src-paths)
                             (apply concat)
                             (distinct))
   :kaocha/test-paths   (->> libs
                             (map :test-paths)
                             (apply concat)
                             (distinct))})

(defn ^:private clj! [dir cmd]
  (-> (deps/clojure cmd {:dir dir, :inherit true})
      (p/check)))

(defn ^:private unit-test [dir]
  (println :running-unit-tests... dir)
  (clj! dir ["-M:test"])
  (println))

(defn build-test-config "Build the .hifi-kaocha.edn test config file" []
  (spit ".hifi-kaocha.edn"
        (kaocha-config (hifi-libs))))

(defn test-main "Run all unit tests in libs/." [& args]
  (build-test-config)
  (apply clojure "-M:dev:test" args))
