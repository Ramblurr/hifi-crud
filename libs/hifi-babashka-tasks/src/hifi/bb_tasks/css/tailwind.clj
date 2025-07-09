(ns hifi.bb-tasks.css.tailwind
  (:require
   [hifi.bb-tasks.config :as config]
   [hifi.error.iface :as pe]
   [babashka.process :refer [shell]]))

(def TailwindConfigSchema
  [:map
   [:input {:doc "Input CSS file for Tailwind CSS"} :string]
   [:output {:doc "Output CSS file for Tailwind CSS"} :string]
   [:tw-binary {:default "tailwindcss"
                :desc    "Path to the Tailwind CSS binary"}
    :string]])

(defn tw-config []
  (try
    (pe/coerce! TailwindConfigSchema
                (->
                 (config/build-config)
                 :hifi/tailwindcss))
    (catch clojure.lang.ExceptionInfo e
      (if (pe/id?  e ::pe/schema-validation-error)
        (do
          (pe/bling-schema-error e)
          (System/exit 1))
        (throw e)))))

(defn build-dev
  "Builds Tailwind CSS in development mode."
  [& _args]
  (let [{:keys [tw-binary input output]} (tw-config)]
    (shell tw-binary "--input" input "--output" output)))

(defn watch-dev
  "Watches Tailwind CSS files for changes and rebuilds in development mode."
  [& _args]
  (let [{:keys [tw-binary input output]} (tw-config)]
    (shell tw-binary "--watch" "--input" input "--output" output)))

(defn build-prod
  "Builds Tailwind CSS in production mode."
  [& _args]
  (let [{:keys [tw-binary input output]} (tw-config)]
    (shell tw-binary "--minify" "--input" input "--output" output)))
