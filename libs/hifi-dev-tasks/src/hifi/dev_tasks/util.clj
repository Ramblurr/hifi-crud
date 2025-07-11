;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.util
  (:require
   [bling.core :as bling]
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

(defn log [style prefix & args]
  (apply println (bling/bling [style prefix]) args))

(def info  (partial log :bold.info  "info "))
(defn debug [& args]
  (when (System/getenv "DEBUG")
    (apply log :bold.purple  "debug " args)))
(def error (partial log :bold.error "error "))

(defn str->keyword
  "Convert a string to a keyword respecting that the input with or without a leading colon"
  [s]
  (when s
    (if (.startsWith s ":")
      (keyword (subs s 1))
      (keyword s))))
