(ns hifi.cli
  (:gen-class)
  (:require
   [hifi.cli.extension :as ext]
   [hifi.cli.terminal :as term]
   [babashka.cli :as cli]
   [hifi.cli.cmd.dev :as cmd.dev]
   [hifi.cli.cmd.new :as cmd.new]))

(declare table)

(defn help-handler
  "Show help for the CLI"
  [_]
  (term/msg "Usage: hifi <command> [OPTIONS]")
  (term/msg)
  (term/msg "This is hifi.")
  (term/msg)
  (term/msg "Commands:")
  (term/msg (cli/format-table
             {:rows (->> table
                         (map (fn [c]
                                (when-not (:hide? c)
                                  [(first (:cmds c))
                                   (:description c)])))
                         (filter some?))
              :indent 2})))

(def table
  [{:cmds ["help"] :fn help-handler :description "Get this help"}
   {:cmds [] :fn help-handler :hide? true}

   cmd.dev/spec])

(def help-spec {:description "Help about any command"
                :doc "This is hifi, the hifi clojure command line interface."
                :spec {:help {:desc  "Show this help message" :alias :h}}
                :fn (fn [{:keys [cmd-tree args bin-name]}]
                      (ext/help {:cmd-tree cmd-tree :args args :bin-name bin-name}))})

(def cmd-tree
  {"help" help-spec
   "dev" cmd.dev/spec
   "new" cmd.new/spec
   :spec {:verbose     {:desc "Verbose output"
                        :coerce  :boolean}
          :debug       {:desc "Print additional logs and traces"
                        :coerce  :boolean}
          :help {:desc  "Show this help message" :alias :h}}})

#_(defn -main [& args]
    (clojure.pprint/pprint table)
    #_(try
        (cli/dispatch table args)
        (catch Exception e
          (term/print-error e))
        (finally
          (shutdown-agents))))

(defn main [& args]
  (ext/with-exception-reporting
    (ext/dispatch cmd-tree args {:middleware ext/default-middleware
                                 :bin-name "hifi"
                                 :description "This is hifi, the hifi clojure command line interface."
                                 :error-fn ext/error-fn})))

(defn -main [& args]
  (binding [ext/*exit?* true]
    (apply main args)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))

(comment

  (apply main ["new" "--wtf"])
  (apply main ["help" "new"])
  ;; rcf
  ;; => "\nCreate a new hifi project in the current directory\n\nUsage: \n  hifi new [flags] <project-name>\n\nExtra docs\n\n\nFlags:\n  --overwrite      false             Whether to overwrite an existing directory\n  --template-coord hifi/cli/template\n  --target-dir                       Defines the directory which the new project is created in.\n                                     By default it will be the name part of your project-name\n                                     example: com.example/my-app -> \"my-app/\"\n  --api                              Creates a smaller stack for data api only apps\n\n"
  (with-out-str
    (apply main []))
  ;; => "This is hifi, the hifi clojure command line interface.\n\nUsage:\n  hifi [flags]\n  hifi [command]\n\nAvailable commands:\n  dev  \tStart a dev repl\n  help \tHelp about any command\n  new  \tCreate a new hifi project in the current directory\n\nFlags:\n      --verbose Verbose output\n      --debug   Print additional logs and traces\n  -h, --help    Show this help message\n\n"

;;
  )
