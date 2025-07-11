;; Copyright © 2022 Logseq
;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; From https://github.com/logseq/dev-tasks/blob/acb3d3d5d38c4ac16f617cb10ae6f99fe1b8de6e/src/logseq/bb_tasks/lint/carve.clj
;; SPDX-License-Identifier: MIT
(ns hifi.dev-tasks.lint.carve
  "This ns adds a more friendly commandline interface to carve by merging
  options to the default config."
  (:require [clojure.edn :as edn]
            [babashka.fs :as fs]
            [pod.borkdude.clj-kondo :as clj-kondo]))

;; HACK: define clj-kondo.core ns which is used by carve
;; TODO: Carve should handle this
(intern (create-ns 'clj-kondo.core) 'run! clj-kondo/run!)
(require '[carve.main])

(defn -main
  "Wrapper around carve.main that defaults to .carve/config.edn and merges
in an optional string of options"
  [& args]
  (let [default-opts (if (fs/exists? ".carve/config.edn")
                       (slurp ".carve/config.edn")
                       "{:paths [\"src\"] :report {:format :ignore}}")
        opts         (if-let [more-opts (first args)]
                       (pr-str (merge (select-keys (edn/read-string default-opts) [:paths :api-namespaces])
                                      (edn/read-string more-opts)))
                       default-opts)]
    (apply carve.main/-main ["--opts" opts])))
