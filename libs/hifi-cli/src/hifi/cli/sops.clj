(ns hifi.cli.sops
  (:require
   [clojure.pprint :as pp]
   [ol.sops :as sops]
   [babashka.fs :as fs]
   [clj-yaml.core :as yaml]
   [hifi.util.os :as os]
   [clojure.string :as str])
  (:import
   [java.security KeyPair]
   [com.exceptionfactory.jagged.x25519 X25519KeyPairGenerator]))

(set! *warn-on-reflection* true)

(defn created-at []
  (str (.truncatedTo (java.time.OffsetDateTime/now)
                     java.time.temporal.ChronoUnit/SECONDS)))

(defn  generate-age-key ^KeyPair []
  (let [gen (X25519KeyPairGenerator.)]
    (.generateKeyPair gen)))

(defn with-keys-txt-comments [lines meta]
  (into []
        (concat lines
                (for [[k v] meta]
                  (str "# " (name k) ": " v)))))

(defn keys-txt-path
  "Returns the absolute path to where the age keys.txt file should be stored.

  Follows platform-specific conventions for configuration file storage as per the [sops documentation](https://github.com/getsops/sops?tab=readme-ov-file#id8).

  All:
  - If the `SOPS_AGE_KEY_FILE` environment variable is set, that path is returned instead.

  Linux:
  - `$XDG_CONFIG_HOME/sops/age/keys.txt` if `XDG_CONFIG_HOME` is set
  - Falls back to `$HOME/.config/sops/age/keys.txt`

  macOS:
  - `$XDG_CONFIG_HOME/sops/age/keys.txt` if `XDG_CONFIG_HOME` is set
  - Falls back to `$HOME/Library/Application Support/sops/age/keys.txt`

  Windows:
  - `%AppData%\\sops\\age\\keys.txt`"
  []
  (or (System/getenv "SOPS_AGE_KEY_FILE")
      (str
       (cond
         (os/windows?)
         (fs/path (System/getenv "AppData") "sops" "age" "keys.txt")

         (os/macos?)
         (if-let [xdg (System/getenv "XDG_CONFIG_HOME")]
           (fs/path xdg "sops" "age" "keys.txt")
           (fs/path (fs/home) "Library" "Application Support" "sops" "age" "keys.txt"))

         :else
         (if-let [xdg (System/getenv "XDG_CONFIG_HOME")]
           (fs/path xdg "sops" "age" "keys.txt")
           (fs/path (fs/home) ".config" "sops" "age" "keys.txt"))))))

(defn keys-txt-entry [^KeyPair keypair & {:keys [created] :as meta}]
  (let [meta (dissoc meta :created)]
    (str/join "\n"
              (cond->
               [(str "# created: " (or created (created-at)))
                (str "# public-key: " (.getPublic keypair))]
                (seq meta) (with-keys-txt-comments meta)
                true       (conj (str (.getPrivate keypair)))))))

(defn write-secret-key
  "Writes the secret key entry to keys.txt, returns the keys.txt path that was written to"
  [{:keys [age-keys-txt-path age-keypair name target-dir]}]
  (try
    (let [path  age-keys-txt-path
          entry (keys-txt-entry age-keypair {:created-by   "hifi"
                                             :project-name name
                                             :project-dir  target-dir})]
      (fs/create-dirs (fs/parent path))
      (spit path (str "\n\n" entry) :append true)
      path)
    (catch Exception e
      e)))

(defn public-key [^KeyPair keypair]
  (str (.getPublic keypair)))

(defn pprint [x]
  (with-out-str
    (binding [*print-namespace-maps* false]
      (pp/pprint x))))

(defn write-initial-secrets [target ^KeyPair keypair initial-secrets]
  (sops/encrypt-to-file target
                        (pprint initial-secrets)
                        {:age (str (.getPublic keypair))
                         :input-type "binary"}))

(comment
  (println)
  (println
   (keys-txt-entry (generate-age-key)
                   {:foo "hello there"}))
  (println
   (keys-txt-entry (generate-age-key)))
  ;; rcf
  )
