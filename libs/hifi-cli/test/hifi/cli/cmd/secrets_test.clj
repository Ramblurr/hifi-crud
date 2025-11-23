(ns hifi.cli.cmd.secrets-test
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [hifi.cli.cmd.secrets :as secrets]
   [hifi.cli.sops :as sops]
   [hifi.cli.terminal :as term]))

(defn- project-fixture []
  (let [root (fs/create-temp-dir {:prefix "hifi-secrets"})
        config-dir (fs/path root "config")
        config-file (fs/path config-dir "hifi.edn")]
    (fs/create-dirs config-dir)
    (spit (str config-file) "{:hifi/application \"demo/app\"}")
    {:root root
     :config-dir config-dir
     :config-file config-file}))

(deftest uses-existing-sops-config
  (testing "we reuse existing .sops.yaml and honor --age overrides"
    (let [{:keys [root config-dir config-file]} (project-fixture)
          target (fs/path config-dir "secrets.dev.sops.edn")
          calls  (atom nil)]
      (spit (str (fs/path root ".sops.yaml")) "creation_rules: []")
      (with-redefs [sops/write-secret-key (fn [_] (throw (ex-info "should not create key" {})))
                    sops/write-initial-secrets (fn [path payload opts]
                                                 (reset! calls {:target path
                                                                :initial payload
                                                                :opts opts}))
                    term/msg (fn [& _] nil)]
        (secrets/new-handler {:opts {:config-file (str config-file)
                                     :name        "secrets.dev.sops.edn"
                                     :age         "age1override"}}))
      (is (= target (:target @calls)))
      (is (= {:recipients "age1override"} (:opts @calls)))
      (is (contains? (:initial @calls) :hifi/root-key)))))

(deftest refuses-when-secrets-exist-without-config
  (testing "we bail if secrets exist but .sops.yaml is missing"
    (let [{:keys [config-dir config-file]} (project-fixture)]
      (spit (str (fs/path config-dir "other.sops.edn")) "secret")
      (with-redefs [sops/write-secret-key (fn [_] (throw (ex-info "should not write key" {})))
                    term/msg (fn [& _] nil)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Found encrypted secrets"
                              (secrets/new-handler {:opts {:config-file (str config-file)
                                                           :name        "new.sops.edn"}})))))))

(deftest bootstraps-config-when-missing
  (testing "we create keys + .sops.yaml if nothing exists"
    (let [{:keys [root config-dir config-file]} (project-fixture)
          key-data (atom nil)
          enc-data (atom nil)
          target   (fs/path config-dir "custom.sops.edn")
          sops-yaml (fs/path root ".sops.yaml")]
      (with-redefs [sops/write-secret-key (fn [data]
                                            (reset! key-data data))
                    sops/write-initial-secrets (fn [path keypair payload]
                                                 (reset! enc-data {:target path
                                                                   :keypair keypair
                                                                   :initial payload}))
                    term/msg (fn [& _] nil)]
        (secrets/new-handler {:opts {:config-file (str config-file)
                                     :name        "custom.sops.edn"}}))
      (is (fs/exists? sops-yaml))
      (let [content (slurp (str sops-yaml))]
        (is (str/includes? content (:age-public-key @key-data)))
        (is (str/includes? content (:age-keys-txt-path @key-data))))
      (is (= target (:target @enc-data)))
      (is (instance? java.security.KeyPair (:keypair @enc-data)))
      (is (contains? (:initial @enc-data) :hifi/root-key)))))

(deftest reports-sops-errors-when-no-recipients
  (testing "missing recipients errors bubble with hints"
    (let [{:keys [root config-file]} (project-fixture)
          sops-yaml (fs/path root ".sops.yaml")]
      (spit (str sops-yaml) "creation_rules: []")
      (let [ex (with-redefs [sops/write-secret-key (fn [_] (throw (ex-info "should not write key" {})))
                             sops/write-initial-secrets (fn [path _payload opts]
                                                          (throw (ex-info "no recipients" {:path path
                                                                                           :opts opts})))
                             term/msg (fn [& _] nil)]
                 (try
                   (secrets/new-handler {:opts {:config-file (str config-file)
                                                :name        "secrets.dev.sops.edn"}})
                   (catch clojure.lang.ExceptionInfo e e)))]
        (is ex "expected error when no recipients are configured")
        (is (str/includes? (ex-message ex) "no recipients"))
        (is (= "Ensure .sops.yaml declares a recipient for .sops.edn files or pass --age."
               (::term/suggest (ex-data ex))))))))
