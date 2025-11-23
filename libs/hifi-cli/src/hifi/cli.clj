(ns hifi.cli
  (:gen-class)
  (:require
   [hifi.cli.cmd.system :as cmd.system]
   [hifi.cli.cmd.dev :as cmd.dev]
   [hifi.cli.cmd.secrets :as cmd.secrets]
   [hifi.cli.cmd.new :as cmd.new]
   [hifi.cli.extension :as ext]))

(def help-spec {:desc "Help about any command"
                :doc "This is hifi, the hifi clojure command line interface."
                :spec {:help {:desc  "Show this help message" :alias :h}}
                :fn (fn [{:keys [cmd-tree args bin-name]}]
                      (ext/help {:cmd-tree cmd-tree :args args :bin-name bin-name}))})

(def cmd-tree
  {"help" help-spec
   "dev" cmd.dev/spec
   "system" cmd.system/spec
   "new" cmd.new/spec
   "secrets" cmd.secrets/spec
   :spec {:verbose     {:desc "Verbose output"
                        :coerce  :boolean}
          :debug       {:desc "Print additional logs and traces"
                        :coerce  :boolean}
          :help {:desc  "Show this help message" :alias :h}}})

(defn main [& args]
  (ext/with-exception-reporting
    (ext/dispatch cmd-tree args {:middleware ext/default-middleware
                                 :bin-name "hifi"
                                 :desc "This is hifi, the hifi clojure command line interface."
                                 :error-fn ext/error-fn})))

(defn -main [& args]
  (binding [ext/*exit?* true]
    (apply main args)
    (ext/exit 0)))

(comment

  (apply main ["new" "--wtf"])
  (apply main ["system" "--help"])
  ;; rcf

;;
  )
