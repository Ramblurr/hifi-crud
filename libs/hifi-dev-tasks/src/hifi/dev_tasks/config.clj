;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.config
  (:require
   [clojure.edn :as edn]
   [hifi.config :as config]
   [hifi.dev-tasks.spec :as spec]
   [hifi.error.iface :as pe]
   [time-literals.data-readers]))

;; Why are these not being picked up?
(set! *data-readers*
      {'time/date            time-literals.data-readers/date
       'time/date-time       time-literals.data-readers/date-time
       'time/zoned-date-time time-literals.data-readers/zoned-date-time
       'time/instant         time-literals.data-readers/instant
       'time/time            time-literals.data-readers/time
       'time/month-day       time-literals.data-readers/month-day
       'time/duration        time-literals.data-readers/duration
       'time/year-month      time-literals.data-readers/year-month})

(defn read-config []
  (config/-read-config "resources/env.edn" nil))

(defn project-meta []
  (-> (read-config)
      :hifi/project))

(defn read-tasks-config
  "Read task configuration under :tasks/config of current bb.edn"
  []
  (-> (System/getProperty "babashka.config")
      slurp
      edn/read-string
      :tasks/config))

(defn load [path spec]
  (try
    (pe/coerce! spec (get-in (read-config) path {}))
    (catch clojure.lang.ExceptionInfo e
      (if (pe/id?  e ::pe/schema-validation-error)
        (do
          (pe/bling-schema-error e)
          (System/exit 1))
        (throw e)))))

(defn tailwindcss []
  (load [:hifi/dev :tailwindcss] spec/TailwindConfigSchema))

(defn bun []
  (load [:hifi/dev :bun] spec/BunConfigSchema))

(defn datomic []
  (load [:hifi/dev :datomic] spec/DatomicConfigSchema))
