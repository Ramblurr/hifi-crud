(ns hifi.cli
  (:require
   [babashka.cli :as cli]))

(def shared-specs {:help {:desc  "Show this help message"
                          :alias :h}})

(defn with-shared-specs [ks spec]
  (reduce (fn [spec k]
            (assoc spec k (get shared-specs k))) spec ks))

(def new-spec-def
  {:spec (with-shared-specs [:help]
           {:project-name {:desc    "THe project name"
                           :ref     "<name>"}})
   :order []})

(defn new-help []
  (println "Usage: hifi new [OPTIONS]")
  (println)
  (println "Start a new hifi application")
  (println)
  (println "OPTIONS:")
  (println (cli/format-opts new-spec-def))
  (println)
  (println "EXAMPLES:")
  (println "todo"))

(defn new
  [{:keys [opts _args]}]
  (if (:help opts)
    (new-help)
    (println "did new")))

(defn help-handler
  "Show help for the CLI"
  [{:keys [args]}]
  (if (seq args)
    (let [cmd (first args)]
      (case cmd
        "new"   (new-help)
        (println (str "Unknown command: " cmd))))
    (do
      (println "Usage: hifi <command> [OPTIONS]")
      (println)
      (println "This is hifi")
      (println)
      (println "Commands:")
      (println "  new     Start a new hifi project"))))

(def table
  [{:cmds ["help"] :fn help-handler}
   {:cmds [] :fn help-handler}
   (merge {:cmds ["new"] :fn new} new-spec-def)])

(defn -main [& args]
  (cli/dispatch table args))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
