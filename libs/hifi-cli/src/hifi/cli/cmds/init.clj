(ns hifi.cli.cmds.init
  (:require
   [hifi.cli.cmds.shared :as shared]))

(declare spec)

(def examples [])

(defn handler
  [{:keys [opts args] :as i}]
  (let [config (shared/load-config opts)]
    (println config)))

(def spec
  (shared/with-help handler
    {:spec (shared/with-shared-specs [:help :config-file] {})
     :args [{:desc    "The name of the project, must be a qualified project name like com.example/my-app"
             :ref     "<project-name>"}]
     :args->opts [:project-name]
     :examples examples
     :description "Create a new hifi project in the current directory"
     :doc "Extra docs"
     :cmds ["init"]}))
