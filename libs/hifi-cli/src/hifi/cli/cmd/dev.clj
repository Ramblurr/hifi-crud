(ns hifi.cli.cmd.dev
  (:require
   [babashka.process :as p]
   [hifi.cli.cmd.shared :as shared]))

((requiring-resolve 'hashp.install/install!))

(def examples [])

(defn handler
  [{:keys [opts _args]}]
  (let [{:keys [config-loader profile]} opts
        _config (shared/load-config opts)]
    (apply p/shell {:continue true}
           (shared/->args
            "clojure"
            "-X:dev"
            "hifi.core.main/start"
            ":profile" profile
            (when config-loader [":config-loader" config-loader])))))

(def spec {:fn handler
           :spec (shared/with-shared-specs [:help :config-file]
                   {:config-loader {:desc "The symbol for an optional qualified function (arity-1, receives opts map) in your program that will load the config, example: org.my-app/load-config"}
                    :profile {:desc "The profile to start the dev environment with"
                              :coerce :keyword
                              :default :dev}})
           :examples examples
           :description "Start a dev repl"
           :cmds ["dev"]})
