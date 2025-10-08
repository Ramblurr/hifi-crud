(ns hifi.cli.cmd.new
  (:require
   [babashka.fs :as fs]
   [clojure.tools.build.tasks.copy] ;; require so that it's compiled and native-image can find it
   [hifi.cli.cmd.shared :as shared]
   [hifi.cli.sops :as sops]
   [hifi.util.codec :as codec]
   [hifi.util.crypto :as crypto]
   [org.corfield.new :as dnew]))

(defn data-fn
  "Example data-fn handler.

  Result is merged onto existing options data."
  [_data]
  ;; (println "data-fn got ")
  ;; (pp/pprint _data)
  (let [age-keypair (sops/generate-age-key)]
    {:age-keypair age-keypair
     :age-keys-txt-path (sops/keys-txt-path)
     :age-public-key (sops/public-key age-keypair)}))

(defn template-fn
  "Example template-fn handler.

  Result is used as the EDN for the template."
  [edn _data]
  ;; (println "template-fn returning edn")
  edn)

(defn initial-secrets []
  {:hifi/root-key (codec/->hex (crypto/bytes 32))})

(defn post-process-fn
  "Example post-process-fn handler.

  Can programmatically modify files in the generated project."
  [_edn data]
  (sops/write-secret-key data)
  (sops/write-initial-secrets (str (fs/path (:target-dir data) "config" "secrets.dev.sops.edn")) (:age-keypair data) (initial-secrets)))

(defn new-project [{:keys [target-dir project-name overwrite template-coord]}]
  ;; (println "template-coord" template-coord)
  (dnew/create {:target-dir target-dir
                :name       project-name
                :src-dirs   ["."]
                :overwrite  overwrite
                :template   template-coord}))

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
  [{:keys [opts args]}]
  (validate-opts opts args)
  (new-project opts))

(def spec
  (shared/with-help handler
    {:spec (shared/with-shared-specs [:help]
             {:overwrite {:coerce :boolean
                          :desc "Whether to overwrite an existing directory"
                          :default false}
              :template-coord {:default "hifi/cli/template"}
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
  (require '[org.corfield.new.impl :as new.impl])

  (let [t "resources/hifi/cli/template"
        opts {:target-dir "new-test"
              :name "foo/my-app"
              :src-dirs ["libs/hifi-cli"]
              :overwrite  true
              :template   t}
        {:keys [git-dir src-dirs template] :as basic-opts} (new.impl/preprocess-options opts)
        [dir edn-file] (new.impl/find-root (cond->> src-dirs
                                             git-dir
                                             (into [git-dir]))
                                           template)]
    edn-file)

  #_(new.impl/find-root
     (new.impl/preprocess-options))

;;
  )
