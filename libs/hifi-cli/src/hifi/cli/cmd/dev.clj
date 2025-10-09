(ns hifi.cli.cmd.dev
  (:require
   [babashka.process :as p]
   [hifi.cli.cmd.shared :as shared]))

(declare spec)

(def examples [])

(defn handler
  [{:keys [profile config-loader] :as opts} _args]
  (let [_config (shared/load-config opts)]
    (apply p/shell {:continue true}
           (shared/->args
            "hifi.core.main/start"
            ":profile" profile
            (when config-loader [":config-loader" config-loader])))))

(def spec {:fn handler
           :spec (shared/with-shared-specs [:help :config-file]
                   {:config-loader {:desc "The symbol for an optional qualified function (arity-1, receives opts map) in your program that will load the config, example: org.my-app/load-config"}
                    :profile {:desc "The profile to start the dev environment with"
                              :default ":dev"}})
           :examples examples
           :description "Start a dev repl"
           :cmds ["dev"]})
