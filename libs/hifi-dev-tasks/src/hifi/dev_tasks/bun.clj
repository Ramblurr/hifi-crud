;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.bun
  (:require
   [babashka.fs :as fs]
   [bling.core :as bling]
   [clojure.string :as str]
   [hifi.dev-tasks.config :as config]
   [hifi.dev-tasks.util :refer [debug shell str->keyword]]))

(defn- bun-installation-info [binary-name]
  (let [path (fs/which binary-name)]
    {:bun-present? (some? path)
     :bun-method   :path-bin}))

(defn using-bun?
  "Checks if the project is using bun or not"
  []
  (true? (:enabled? (config/bun))))

(defn exit-bun-not-configured []
  (bling/callout {:type  :error
                  :theme :gutter}
                 (bling/bling
                  "Project is not configured to use Bun"
                  "\n\n"
                  "Add a " [:italic ":bun"]
                  " configuration to the your " [:italic ":hifi/dev"]
                  " section in "
                  [:italic "env.edn"]))
  (System/exit 1))

(defn exit-bun-not-installed [path]
  (bling/callout {:type  :error
                  :theme :gutter}
                 (bling/bling
                  "bun is not installed on your system."
                  "\n\n"
                  "Please install the standalone bun cli binary and ensure that "
                  [:italic path]
                  " is available in your PATH."))
  (System/exit 1))

(defn exit-bun-unknown-profile [profile-name]
  (bling/callout {:type  :error
                  :theme :gutter}
                 (bling/bling
                  "Unknown Bun profile: "
                  [:italic profile-name]
                  "\n\n"
                  "Please check your configuration in "
                  [:italic "env.edn"]
                  " under the " [:italic ":hifi/dev :bun"] " section."))
  (System/exit 1))

(defn ensure-bun [{:keys [path]}]
  (let [{:keys [bun-present?]} (bun-installation-info path)]
    (when-not bun-present?
      (exit-bun-not-installed path)))
  (when-not (using-bun?)
    (exit-bun-not-configured)))

(defn bun-opts [{:keys [path]} extra]
  (into [] (concat
            [path]
            (or extra []))))

(defn bun-profile [conf key]
  (get-in conf [:profiles key] nil))

(defn exec-bun-profile [{:keys [path] :as conf} profile-name target]
  (ensure-bun conf)
  (if-let [profile (bun-profile conf profile-name)]
    (let [{:keys [cd env args prod dev]} profile
          target-args                    (:args (if (= target :prod) prod dev))
          final-args                     (apply conj ["build"] (apply conj  target-args args))
          outdir-arg                     (->> (concat args target-args)
                                              (filter #(str/starts-with? % "--outdir="))
                                              first)
          outdir                         (when outdir-arg
                                           (subs outdir-arg 9))]
      (when outdir
        (fs/create-dirs (fs/path cd outdir)))
      (debug "[bun] env :" env)
      (debug "[bun] cd  :" cd)
      (debug "[bun] args:" target-args)
      (debug "[bun] args:" final-args)
      (try
        (apply shell {:dir       cd
                      :extra-env env}
               path
               final-args)
        (catch clojure.lang.ExceptionInfo e
          (when-not (-> (ex-data e) :exit)
            (throw e)))))

    (exit-bun-unknown-profile profile-name)))

(defn choose-default-profile [conf]
  (let [profile-names (keys (get-in conf [:profiles]))]
    (if (= 1 (count profile-names))
      (first profile-names)
      :default)))

(defn choose-profile [conf maybe-profile-name]
  (if maybe-profile-name
    (if (get-in conf [:profiles maybe-profile-name])
      maybe-profile-name
      (exit-bun-unknown-profile maybe-profile-name))
    (choose-default-profile conf)))

(defn build-dev
  "Builds Bun CSS in development mode."
  [& args]
  (let [conf          (config/bun)
        provided-name (str->keyword (first args))]
    (exec-bun-profile conf
                      (choose-profile conf provided-name)
                      :dev)))
