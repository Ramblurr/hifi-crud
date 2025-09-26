(ns hifi.cli
  (:require
   [hifi.cli.cmds.dev :as cmd.dev]
   [hifi.cli.cmd.new :as cmd.new]
   [babashka.cli :as cli]))

(declare table)

(defn help-handler
  "Show help for the CLI"
  [_]
  (println "Usage: hifi <command> [OPTIONS]")
  (println)
  (println "This is hifi.")
  (println)
  (println "Commands:")
  (println (cli/format-table
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
   cmd.new/spec
   cmd.dev/spec])

(defn -main [& args]
  (cli/dispatch table args))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
