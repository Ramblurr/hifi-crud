;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.util
  "Misc util fns"
  (:require
   [babashka.process :as p]))

(defn shell
  "Wrapper around `babashka.process/shell` that does not print errors when the user SIGINTs / Control+Cs"
  [& args]
  (try
    (apply p/shell args)
    (catch Exception e
      ;; SIGINT = 128 + 2 = 130, probably something else on Windows
      (when-not (= 130 (-> e ex-data :exit))
        (throw e)))))
