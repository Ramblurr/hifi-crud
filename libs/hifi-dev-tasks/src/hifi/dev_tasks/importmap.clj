;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.importmap
  (:require
   [babashka.cli :as cli]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [hifi.dev-tasks.importmap.npm :as npm]
   [hifi.dev-tasks.importmap.package :as pkg]))

(def default-opts {:vendor-path    "assets/vendor/"
                   :assets-route   "/assets"
                   :importmap-path "assets/importmap.edn"})

(defn cmd-pin [opts]
  (pkg/pin-packages (merge default-opts
                           (select-keys (:opts opts) [:env :preloads :packages :from]))))

(defn cmd-unpin [opts]
  (pkg/unpin-packages (merge default-opts (select-keys (:opts opts) [:env :packages :from]))))

(defn cmd-pristine [opts]
  (let [opts         (merge default-opts
                            (select-keys (:opts opts) [:env :preloads :from]))
        pkg-versions (pkg/package-versions (pkg/read-importmap opts))
        packages     (map #(str/join "@" %) pkg-versions)]
    (pkg/pin-packages (assoc opts :packages packages))))

(defn cmd-json [_opts]
  (print
   (pkg/to-json-importmap default-opts)))

(defn cmd-outdated [_opts]
  (let [pkg-versions  (pkg/package-versions (pkg/read-importmap default-opts))
        outdated-pkgs (npm/outdated pkg-versions)]
    (pp/print-table [:name :current :latest :error]
                    outdated-pkgs)))
(defn cmd-list [_opts]
  (->> (:pins (pkg/read-importmap default-opts))
       (map (fn [[name {:keys [version to preloads]}]]
              {:name     name
               :version  version
               :to       to
               :preloads (if (false? preloads)
                           false
                           true)}))
       (sort-by :name)
       (pp/print-table [:name :to :version :preloads])))

(defn help [& _args]
  (println "Available commands:"))

(def commands
  [{:cmds [] :fn help}
   {:cmds       ["pin"]
    :fn         cmd-pin
    :aliases    {:f :from
                 :e :env
                 :p :preloads}
    :coerce     {:from     :string
                 :packages []}
    :args->opts (repeat :packages)}
   {:cmds       ["unpin"]
    :fn         cmd-unpin
    :aliases    {:f :from
                 :e :env}
    :coerce     {:packages []}
    :args->opts (repeat :packages)}
   {:cmds    ["pristine"]
    :fn      cmd-pristine
    :aliases {:f :from
              :e :env
              :p :preloads}
    :coerce  {:from :string}}
   {:cmds ["json"]
    :fn   cmd-json}
   {:cmds ["outdated"]
    :fn   cmd-outdated}
   {:cmds ["list"]
    :fn   cmd-list}])

(defn -main
  ([& args]
   (cli/dispatch commands args)))
