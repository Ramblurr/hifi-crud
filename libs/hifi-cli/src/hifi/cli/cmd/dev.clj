(ns hifi.cli.cmd.dev
  (:require
   [hifi.cli.cmd.shared :as shared]))

(declare spec)

(def examples [])

(defn handler
  [{:keys [opts _args]}]
  (let [config (shared/load-config (assoc opts :profile :dev))]
    (println config)))

(def spec (shared/with-help handler
            {:spec (shared/with-shared-specs [:help :config-file])
             :examples examples
             :description "Start a dev repl"
             :cmds ["dev"]}))
