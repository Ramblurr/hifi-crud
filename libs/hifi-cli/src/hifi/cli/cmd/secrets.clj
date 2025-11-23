(ns hifi.cli.cmd.secrets
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hifi.cli.cmd.new :as cmd.new]
   [hifi.cli.cmd.shared :as shared]
   [hifi.cli.sops :as sops]
   [hifi.cli.terminal :as term]))

((requiring-resolve 'hashp.install/install!))

(def examples [])

(def ^:private sops-template "hifi/cli/template/stubs/.sops.yaml.tmpl")

(defn- project-paths [config-file]
  (let [config-path  (fs/canonicalize (fs/path config-file))
        config-dir   (fs/parent config-path)
        project-root (fs/parent config-dir)]
    {:config-path  config-path
     :config-dir   config-dir
     :project-root project-root
     :project-name (fs/file-name project-root)}))

(defn- config-secrets [config-dir]
  (when (fs/exists? config-dir)
    (->> (fs/list-dir config-dir)
         (filter #(and (fs/regular-file? %)
                       (str/ends-with? (str (fs/file-name %)) ".sops.edn"))))))

(defn- render-sops-template [data]
  (let [resource (io/resource sops-template)]
    (when-not resource
      (throw (term/error (format "Unable to locate %s on the classpath" sops-template))))
    (-> (slurp resource)
        (str/replace "{{age-public-key}}" (:age-public-key data))
        (str/replace "{{age-keys-txt-path}}" (:age-keys-txt-path data)))))

(defn- write-sops-config! [project-root data]
  (let [target (fs/path project-root ".sops.yaml")]
    (spit (str target) (render-sops-template data))
    target))

(defn- trim-nil [s]
  (when (some? s)
    (let [candidate (str/trim (str s))]
      (when (seq candidate)
        candidate))))

(defn- encrypt-with-existing-config! [output-file initial-secrets recipients sops-config]
  (try
    (sops/write-initial-secrets output-file initial-secrets
                                (cond-> {}
                                  recipients (assoc :recipients recipients)))
    (catch Exception e
      (throw (term/error (format "sops failed to encrypt secrets: %s" (ex-message e))
                         {::term/suggest (format "Ensure %s declares a recipient for .sops.edn files or pass --age."
                                                 (fs/file-name sops-config))}
                         e)))))

(defn- create-sops-setup! [{:keys [project-root app-name output-file initial-secrets]}]
  (let [data (cmd.new/data-fn {:target-dir (str project-root)
                               :name       app-name})]
    (sops/write-secret-key data)
    (write-sops-config! project-root data)
    (sops/write-initial-secrets output-file (:age-keypair data) initial-secrets)))

(defn new-handler [{:keys [opts]}]
  (let [config (shared/load-config opts)
        {:keys [config-dir project-root]} (project-paths (:config-file opts))
        output-file (fs/path config-dir (:name opts))
        sops-config (fs/path project-root ".sops.yaml")]
    (when (fs/exists? output-file)
      (throw (term/error (format "The secrets file '%s' already exists, will not overwrite it." output-file))))
    (let [project-name    (some-> project-root fs/file-name str)
          app-name        (or (:hifi/application config)
                              project-name
                              "hifi-app")
          initial-secrets (cmd.new/initial-secrets)
          age-recipient   (trim-nil (:age opts))]
      (cond
        (fs/exists? sops-config)
        (do
          (encrypt-with-existing-config! output-file initial-secrets age-recipient sops-config)
          (term/msg (str "Wrote " output-file)))

        (seq (config-secrets config-dir))
        (throw (term/error (format "Found encrypted secrets in '%s' but no .sops.yaml; refusing to guess which key to use."
                                   config-dir)
                           {::term/suggest "Commit or recreate your existing .sops.yaml or provide --age with the correct recipient."}))

        :else
        (do
          (create-sops-setup! {:project-root project-root
                               :app-name     app-name
                               :output-file  output-file
                               :initial-secrets initial-secrets})
          (term/msg (str "Generated .sops.yaml and wrote " output-file)))))))

(def spec {:fn          (fn [_])
           :examples    examples
           :desc        "Work with your application's secrets"
           #_#_:cmds        ["secrets"]
           "new"    {:spec        (shared/with-shared-specs [:help :config-file]
                                    {:name {:default "secrets.dev.sops.edn"
                                            :require true
                                            :alias :n
                                            :desc "The name of the secrets file to create"}
                                     :age  {:alias :a
                                            :desc  "Override recipients passed to sops --age"}})
                     :desc        "Create a new secrets file"
                     :fn          new-handler
                     #_#_:cmds        ["new"]}})
