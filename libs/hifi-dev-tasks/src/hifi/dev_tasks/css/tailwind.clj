;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.css.tailwind
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [bling.core :as bling]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hifi.dev-tasks.config :as config]
   [hifi.dev-tasks.util :refer [error info shell debug]]))

(defn- tailwind-installation-info [binary-name]
  (let [path (fs/which binary-name)]
    {:tailwind-present? (some? path)
     :tailwind-method   :path-bin}))

(defn using-tailwind?
  "Checks if the project is using tailwind or not"
  []
  (true? (:enabled? (config/tailwindcss))))

(defn exit-tailwind-not-configured []
  (bling/callout {:type  :error
                  :theme :gutter}
                 (bling/bling
                  "Project is not configured to use Tailwind CSS"
                  "\n\n"
                  "Add a " [:italic ":tailwindcss"]
                  " configuration to the your " [:italic ":hifi/dev"]
                  " section in "
                  [:italic "env.edn"]
                  ", or create a "
                  [:italic "resources/public/tailwind.css"]
                  " file to use the default Tailwind CSS input."))
  (System/exit 1))

(defn exit-tailwind-not-installed [path]
  (bling/callout {:type  :error
                  :theme :gutter}
                 (bling/bling
                  "tailwindcss is not installed on your system."
                  "\n\n"
                  "Please install the standalone tailwindcss cli binary and ensure that "
                  [:italic path]
                  " is available in your PATH."))
  (System/exit 1))

(defn ensure-tailwind [{:keys [path]}]
  (let [{:keys [tailwind-present?]} (tailwind-installation-info path)]
    (when-not tailwind-present?
      (exit-tailwind-not-installed path)))
  (when-not (using-tailwind?)
    (exit-tailwind-not-configured)))

(defn tailwind-opts [{:keys [path input output]} extra]
  (into [] (concat
            [path]
            (or extra [])
            ["--input" input
             "--output" output])))

(defn tw [conf & extra]
  (ensure-tailwind conf)
  (let [args (tailwind-opts conf extra)]
    (debug "[tailwind] args: " args)
    (apply shell args)))

(defn build-dev
  "Builds Tailwind CSS in development mode."
  [& _args]
  (tw (config/tailwindcss)))

(defn watch-dev
  "Watches Tailwind CSS files for changes and rebuilds in development mode."
  [& _args]
  (tw (config/tailwindcss) "--watch"))

(defn build-prod
  "Builds Tailwind CSS in production mode."
  [& _args]
  (tw (config/tailwindcss) "--minify"))

(defn start-tailwind []
  (info "[tailwind] starting tailwindcss process")
  (let [tailwind-process (apply p/process {:out      :stream
                                           :err      :out
                                           :shutdown p/destroy-tree}
                                (tailwind-opts (config/tailwindcss) ["--watch"]))]
    (with-open [rdr (io/reader (:out tailwind-process))]
      (binding [*in* rdr]
        (loop [last-state nil]
          (let [line  (read-line)
                state (cond
                        (str/blank? line)                   nil
                        (re-find #"^Done in .*s$" line)     :ok
                        (re-find #"^Error.*" line)          (do
                                                              (error (str "[tailwind] " line))
                                                              :error)
                        (re-find #"^tailwindcss v.*$" line) (do
                                                              (info (str "[tailwind] " line))
                                                              :version)
                        :else                               (do (info (str "[tailwind] " line))
                                                                :ok))]
            (when (and  (= last-state :error) (= state :ok))
              (info "[tailwind] recovered from error"))
            (recur (or state last-state))))))

    (fn []
      (info "[tailwind] stopping tailwindcss process")
      (p/destroy-tree tailwind-process))))
