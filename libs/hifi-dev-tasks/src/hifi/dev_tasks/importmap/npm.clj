;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.importmap.npm
  (:require
   [babashka.http-client :as http]
   [cheshire.core :as cheshire]
   [version-clj.core :as version]))

(def endpoint "https://registry.npmjs.org/")

(defn package-meta! [name]
  (let [{:keys [status body]} (http/request {:uri (str endpoint name) :method :get :headers {"Accept" "application/json"} :throw false})]
    (if (= 200 status)
      {:body (cheshire/parse-string body keyword)}
      {:error (cheshire/parse-string body keyword)})))

(defn find-latest-version [response]
  (if-let [v (get-in response [:dist-tags :latest])]
    v
    (when (:versions response)
      (when-let [versions (keys (:versions response))]
        (->> versions
             (filter #(try (version/version->seq %) true
                           (catch Exception _ false)))
             (sort version/version-compare)
             last)))))

(defn outdated? [current-version latest-version]
  (when (and current-version latest-version)
    (neg? (version/version-compare current-version latest-version))))

(defn calc-outdated [packages]
  (for [pkg packages]
    (let [[name version]       pkg
          {:keys [body error]} (package-meta! name)]
      (if error
        {:name  name
         :error (or (:error error) "npm registry error")}
        (let [latest-version (find-latest-version body)]
          {:name      name
           :outdated? (outdated? version latest-version)
           :current   version
           :latest    latest-version})))))
(defn outdated [packages]
  (->>
   packages
   (filter #(some? (second %)))
   (calc-outdated)
   (filter (fn [p] (or (:outdated? p) (:error p))))
   (sort-by :name)))
