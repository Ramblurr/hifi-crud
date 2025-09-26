(ns hifi.cli.cmds.dev
  (:require
   [hifi.cli.cmds.shared :as shared]))

(declare spec)

(def examples [])

(defn handler
  [{:keys [opts _args] :as i}]
  (let [config (shared/load-config opts)]
    (println config)))

(def spec (shared/with-help handler
            {:spec (shared/with-shared-specs [:help :config-file]
                     {:project-name {:desc    "THe project name"
                                     :ref     "<name>"}})
             :examples examples
             :description "Start a dev repl"
             :cmds ["dev"]}))
