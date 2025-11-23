(ns hifi.cli.cmd.system
  (:require
   [babashka.process :as p]
   [hifi.cli.cmd.shared :as shared]
   [hifi.cli.extension :as ext]))

(def examples [])

(def format-spec {:coerce :keyword :default-desc "portal" :validate #{:print :portal}})

(defn system-inspect-handler  [{:keys [opts _args]}]
  (let [{:keys [config-loader profile format]} opts
        {:keys [exit]} (apply p/shell {:continue true}
                              (shared/->args
                               "clojure"
                               "-X:dev"
                               "hifi.dev.system/inspect-system"
                               ":profile" profile
                               ":format" format
                               (when config-loader [":config-loader" config-loader])))]
    (ext/exit exit)))

(def spec {:fn          (fn [_])
           :examples    examples
           :desc        "Explore your application's system map"
           :cmds        ["system"]
           "inspect"    {:spec        (shared/with-shared-specs [:help :config-file :config-loader :profile :unmasked]
                                        {:format format-spec})
                         :desc        "Inspect your application's system map"
                         :fn          system-inspect-handler
                         :cmds        ["inspect"]}})
