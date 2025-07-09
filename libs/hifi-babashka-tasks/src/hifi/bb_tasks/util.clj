;; Copyright © 2022 Logseq
;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; From https://github.com/logseq/bb-tasks/blob/acb3d3d5d38c4ac16f617cb10ae6f99fe1b8de6e/src/logseq/bb_tasks/util.clj
;; SPDX-License-Identifier: MIT
(ns hifi.bb-tasks.util
  "Misc util fns"
  (:require [clojure.edn :as edn]))

(defn read-tasks-config
  "Read task configuration under :tasks/config of current bb.edn"
  []
  (-> (System/getProperty "babashka.config")
      slurp
      edn/read-string
      :tasks/config))
