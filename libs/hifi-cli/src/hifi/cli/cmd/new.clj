(ns hifi.cli.cmd.new
  (:require
   [org.corfield.new :as dnew]
   [rewrite-clj.zip :as z]
   [clojure.pprint :as pp]
   [hifi.cli.cmds.shared :as shared]))

(defn data-fn
  "Example data-fn handler.

  Result is merged onto existing options data."
  [data]
  ;; returning nil means no changes to options data
  (println "data-fn returning nil")
  nil)

(defn template-fn
  "Example template-fn handler.

  Result is used as the EDN for the template."
  [edn data]
  ;; must return the whole EDN hash map
  (println "template-fn returning edn")
  edn)

(defn post-process-fn
  "Example post-process-fn handler.

  Can programmatically modify files in the generated project."
  [edn data]
  (println "post-process-fn not modifying" (:target-dir data)))

(defn new-project [{:keys [target-dir project-name overwrite]}]
  (dnew/create {:target-dir target-dir
                :name       project-name
                :overwrite  overwrite
                :template   "hifi/cli/template"}))

(declare spec)

(def examples [])

(defn valid-project-name?
  [project-name]
  (let [project-sym (try (read-string project-name) (catch Exception _))]
    (or (qualified-symbol? project-sym)
        (and (symbol? project-sym) (re-find #"\." (name project-sym))))))

(defn validate-opts [opts _args]
  (if-let [project-name-str (:project-name opts)]
    (when-not (valid-project-name? project-name-str)
      (shared/exit-msg "<project-name> must be a qualified clojure symbol, for example com.example/my-project"))
    (shared/exit-msg "<project-name> must be provided. see hifi new --help")))

(defn handler
  [{:keys [opts args] :as i}]
  (validate-opts opts args)
  (new-project opts))

(def spec
  (shared/with-help handler
    {:spec (shared/with-shared-specs [:help]
             {:overwrite {:coerce :boolean
                          :desc "Whether to overwrite an existing directory"
                          :default false}
              :target-dir {:desc "Defines the directory which the new project is created in. \nBy default it will be the name part of your project-name\nexample: com.example/my-app -> \"my-app/\" "}
              :api {:coece :boolean
                    :desc "Creates a smaller stack for data api only apps"}})
     :args [{:desc    "The name of the project, must be a qualified project name like com.example/my-app"
             :ref     "<project-name>"}]
     :args->opts [:project-name]
     :examples examples
     :description "Create a new hifi project in the current directory"
     :doc "Extra docs"
     :cmds ["new"]}))

(comment

;;
  )
