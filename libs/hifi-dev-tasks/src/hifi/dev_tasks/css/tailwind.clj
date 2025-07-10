;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.css.tailwind
  (:require
   [bling.core :as bling]
   [babashka.fs :as fs]
   [hifi.dev-tasks.config :as config]
   [hifi.dev-tasks.util :refer [shell]]
   [hifi.error.iface :as pe]))

(def ^:private default-input "resources/public/tailwind.css")

(def TailwindConfigSchema
  [:map {:name :hifi/tailwindcss}
   [:input {:doc     "Input CSS file for Tailwind CSS"
            :default default-input} :string]
   [:output {:doc     "Output CSS file for Tailwind CSS"
             :default "target/resources/public/compiled.css"} :string]
   [:tw-binary {:default "tailwindcss"
                :desc    "Path to the Tailwind CSS binary"}
    :string]])

(defn- tailwind-installation-info [binary-name]
  (let [path (fs/which binary-name)]
    {:tailwind-present? (some? path)
     :tailwind-method   :path-bin}))

(defn- tw-config []
  (let [tw-conf (:hifi/tailwindcss (config/read-config) {})]
    (try
      (pe/coerce! TailwindConfigSchema tw-conf)
      (catch clojure.lang.ExceptionInfo e
        (if (pe/id?  e ::pe/schema-validation-error)
          (do
            (pe/bling-schema-error e)
            (System/exit 1))
          (throw e))))))

(defn using-tailwind?
  "Checks if the project is using tailwind or not"
  []
  (or
   (some? (:hifi/tailwindcss (config/read-config)))
   (fs/exists? (fs/file default-input))))

(defn exit-tailwind-not-configured []
  (bling/callout {:type  :error
                  :theme :gutter}
                 (bling/bling
                  "Project is not configured to use Tailwind CSS"
                  "\n\n"
                  "Add a " [:italic ":hifi/tailwindcss"]
                  " configuration to your "
                  [:italic "env.edn"]
                  ", or create a "
                  [:italic "resources/public/tailwind.css"]
                  " file to use the default Tailwind CSS input."))
  (System/exit 1))

(defn exit-tailwind-not-installed [tw-binary]
  (bling/callout {:type  :error
                  :theme :gutter}
                 (bling/bling
                  "tailwindcss is not installed on your system."
                  "\n\n"
                  "Please install the standalone tailwindcss cli binary and ensure that "
                  [:italic tw-binary]
                  " is available in your PATH."))
  (System/exit 1))

(defn ensure-tailwind [{:keys [tw-binary]}]
  (let [{:keys [tailwind-present?]} (tailwind-installation-info tw-binary)]
    (when-not tailwind-present?
      (exit-tailwind-not-installed tw-binary)))
  (when-not (using-tailwind?)
    (exit-tailwind-not-configured)))

(defn build-dev
  "Builds Tailwind CSS in development mode."
  [& _args]
  (let [{:keys [tw-binary input output] :as conf} (tw-config)]
    (ensure-tailwind conf)
    (shell tw-binary "--input" input "--output" output)))

(defn watch-dev
  "Watches Tailwind CSS files for changes and rebuilds in development mode."
  [& _args]
  (let [{:keys [tw-binary input output] :as conf} (tw-config)]
    (ensure-tailwind conf)
    (shell tw-binary "--watch" "--input" input "--output" output)))

(defn build-prod
  "Builds Tailwind CSS in production mode."
  [& _args]
  (let [{:keys [tw-binary input output] :as conf} (tw-config)]
    (ensure-tailwind conf)
    (shell tw-binary "--minify" "--input" input "--output" output)))
