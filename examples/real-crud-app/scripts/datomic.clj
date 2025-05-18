;; Copyright © 2025 Filipe Silva
;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT
;;
;; This file is based on Filipe Silva's datomic-pro-manager[0]
;;
;; It has been modified accordingly:
;;   - Download functionality removed, we expect to be running in a nix environment
;;     where the datomic-pro-flake is available (see flake.nix)
;;   - All commands changed to exec the `datomic-*` binaries installed by datomic-pro-flake
;;   - All data/files are stored in the ./datomic directory
;;     ./datomic/storage: sqlite file
;;     ./datomic/config: transactor properties
;;   - The sqlite init and default transactor properties values have been inlined
;;
;; Usage: Add to your bb.edn
;;
;;    {:paths ["scripts"]
;;     :deps  {io.github.paintparty/bling {:mvn/version "0.4.2"}}
;;     :tasks {datomic {:task (exec 'datomic/-main)}}}
;;
;; And call with `bb datomic help`
;;
;; [0]:  https://github.com/filipesilva/datomic-pro-manager
(ns datomic
  (:refer-clojure :exclude [test])
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.process :as process]
            [bling.core :as bling]
            [clojure.edn :as edn]
            [clojure.string :as str])
  (:import [java.net Socket SocketException]))

;; sqlite init
(def sqlite-init
  "-- same as Rails 8.0
PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA mmap_size = 134217728; -- 128 megabytes
PRAGMA journal_size_limit = 67108864; -- 64 megabytes
PRAGMA cache_size = 2000;

-- datomic schema
CREATE TABLE datomic_kvs (
    id TEXT NOT NULL,
    rev INTEGER,
    map TEXT,
    val BYTEA,
    CONSTRAINT pk_id PRIMARY KEY (id)
);")

;; transactor properties
(def transactor-properties
  "port=4334
  host=0.0.0.0

  storage-access=remote
  protocol=sql
  sql-driver-class=org.sqlite.JDBC
  sql-url=jdbc:sqlite:./datomic/storage/sqlite.db

  # See https://docs.datomic.com/on-prem/capacity.html
  memory-index-threshold=32m
  memory-index-max=256m
  object-cache-max=128m")

;; logging

(defn log [style prefix & args]
  (apply println (bling/bling [style prefix]) args))

(def info  (partial log :bold.info  "info ")) ;; blue
(def error (partial log :bold.error "error")) ;; red
(def run   (fn [& args]
             (apply log :purple "run  " args)
             (try
               (process/shell args)
               (catch Exception _
                 (error "run failed")
                 (System/exit 1)))))

;; config

(def default-config
  {:datomic-transactor-properties transactor-properties
   :datomic-db-uri                "datomic:sql://{db-name}?jdbc:sqlite:./datomic/storage/sqlite.db"
   :storage-type                  :sqlite
   :sqlite-version                "3.47.0.0" ;; will be picked up from deps.edn
   :sqlite-init                   sqlite-init
   :postgresql-version            "42.7.5"   ;; will be picked up from deps.edn
   })

(defn slurp-edn [x]
  (try
    (-> x slurp edn/read-string)
    (catch Exception _)))

(def deps-config
  (let [deps-edn (slurp-edn "deps.edn")
        dpm-edn  (slurp-edn "dpm.edn")]
    (->> dpm-edn
         (merge {:datomic-version    (get-in deps-edn [:deps 'com.datomic/peer :mvn/version])
                 :sqlite-version     (get-in deps-edn [:deps 'org.xerial/sqlite-jdbc :mvn/version])
                 :postgresql-version (get-in deps-edn [:deps 'org.postgresql/postgresql :mvn/version])})
         (filter (comp some? second))
         (into {}))))

(def config
  (merge default-config deps-config))

(def sqlite? (= (:storage-type config) :sqlite))
(def sqlite-version (:sqlite-version config))
(def postgresql? (= (:storage-type config) :postgresql))
(def postgresql-version (:postgresql-version config))

;; sqlite

(defn sqlite-exists? []
  (fs/exists? (fs/path "./datomic/storage/sqlite.db")))

(defn sqlite-create [& _]
  (cond
    (not sqlite?)
    (info "SQLite disabled")

    (sqlite-exists?)
    (info "SQLite db already exists at ./datomic/storage/sqlite.db")

    :else
    (let [init-f (doto (fs/create-temp-file)
                   fs/delete-on-exit)]
      (info "Creating SQLite db at ./datomic/storage/sqlite.db")
      (spit (str init-f) (:sqlite-init config))
      (run "mkdir -p ./datomic/storage")
      (run (str "sqlite3 ./datomic/storage/sqlite.db -init " init-f " .exit")))))

(defn sqlite-delete [m]
  (cond
    (not sqlite?)
    (info "SQLite disabled")

    (-> m :opts :yes)
    (do
      (info "Deleting ./datomic/storage")
      (run "rm -rf ./datomic/storage"))

    :else
    (do
      (info "This command will delete your SQLite database!")
      (info "Run this command again with --yes to confirm"))))

;; datomic

(def transactor-properties-target-path
  (str (fs/path "./datomic/config/transactor.properties")))

(defn datomic-exists? []
  (fs/exists? (fs/path transactor-properties-target-path)))

(defn prepare-transactor-properties []
  (if (:datomic-transactor-properties-path config)
    (when (or (not (fs/exists? transactor-properties-target-path))
              (not= (slurp (:datomic-transactor-properties-path config))
                    (slurp transactor-properties-target-path)))
      (info "Setting transactor properties")
      (run (str "cp " (:datomic-transactor-properties-path config) " " transactor-properties-target-path)))
    (when (or (not (fs/exists? transactor-properties-target-path))
              (not= (:datomic-transactor-properties config)
                    (slurp transactor-properties-target-path)))
      (info "Setting transactor properties")
      (spit transactor-properties-target-path (:datomic-transactor-properties config)))))

(defn datomic-create []
  (if (datomic-exists?)
    (info "Datomic config already exists at" transactor-properties-target-path)
    (do
      (info "Creating Datomic config at " transactor-properties-target-path)
      (run (format  "mkdir -p %s" (fs/parent transactor-properties-target-path)))
      (prepare-transactor-properties))))

(defn datomic-delete []
  (info "Deleting Datomic config at ./datomic/config")
  (run "rm -rf ./datomic/config"))

(defn port-taken?
  "Returns true if host:port is taken, host defaults to localhost."
  ([port]
   (port-taken? "localhost" port))
  ([host port]
   (try
     (.close (Socket. host port))
     true
     (catch SocketException _
       false))))

(defn running? []
  (port-taken? 4334))

(defn locate-binary [binary]
  (let [{:keys [exit out]} (process/shell
                            {:continue true :out :string :err :string}
                            (str "which " binary))]
    (when (= 0 exit)
      (str/trim out))))

(defn transactor-entrypoint []
  (locate-binary "datomic-transactor"))

(defn get-datomic-version []
  (some->
   (transactor-entrypoint)
   (fs/file)
   (fs/parent)
   (fs/parent)
   (fs/path "share/datomic-pro/VERSION")
   (str)
   (slurp)))

(defn up [_]
  (if-let [transactor (transactor-entrypoint)]
    (do
      (when (and sqlite? (not (sqlite-exists?)))
        (sqlite-create))
      (when-not (datomic-exists?)
        (datomic-create))
      (if (running?)
        (info "Datomic is already running")
        (do
          (info "Starting Datomic")
          (run
           (str transactor " " transactor-properties-target-path)))))
    (error "datomic-transactor not found")))

(defn test [_]
  (let [db-uri (str/replace (:datomic-db-uri config) "{db-name}" "*")
        proc   #(process/shell {:continue     true
                                :pre-start-fn (fn [args] (apply log :purple "run  " (:cmd args)))}
                               "clojure"
                               "-Sdeps" {:deps {'com.datomic/peer
                                                (if-let [peer-dep (System/getenv "DATOMIC_PRO_PEER_JAR")]
                                                  {:local/root peer-dep}
                                                  {:mvn/version (get-datomic-version)})
                                                'org.xerial/sqlite-jdbc    {:mvn/version sqlite-version}
                                                'org.postgresql/postgresql {:mvn/version postgresql-version}
                                                'org.slf4j/slf4j-nop       {:mvn/version "2.0.9"}}}
                               "-M" "--eval"
                               (format
                                "
(require '[datomic.api :as d])
(d/get-database-names \"%s\")
(shutdown-agents)"
                                db-uri))]
    (info (format "Testing connection to %s..." (:datomic-db-uri config)))
    (if (-> (proc) :exit (= 0))
      (info "Connection test successful")
      (do
        (error "Connection test failed")
        (System/exit 1)))))

(defn datomic-bin-relative-db-uri [db-name]
  (-> (:datomic-db-uri config)
      (str/replace "{db-name}" db-name)))

(defn datomic-bin-absolute-db-uri [db-name]
  (prn "GOT " (:datomic-db-uri config))
  (-> (:datomic-db-uri config)
      (str/replace "?jdbc:sqlite:./datomic/storage/sqlite.db" (format "?jdbc:sqlite:%s" (fs/absolutize "./datomic/storage/sqlite.db")))
      (str/replace "{db-name}" db-name)))

(defn console-entrypoint []
  (locate-binary "datomic-console"))

(defn console
  [_]
  ;; https://docs.datomic.com/resources/console.html
  ;; note that the transactor-url does not include db
  (info "Starting Datomic Console")
  (let [db-uri  (datomic-bin-relative-db-uri "")
        console (console-entrypoint)]
    (if console
      (run
       (format "%s -p 4335 console %s" console db-uri))
      (error "datomic-console not found"))))

(defn db-name-req [db-name]
  (when-not db-name
    (error "db-name is required")
    (System/exit 1)))

(defn backup [{{:keys [db-name]} :opts}]
  (db-name-req db-name)
  (info "Backing up" db-name "to" (str "./datomic/backups/" db-name))
  (let [db-uri     (datomic-bin-absolute-db-uri db-name)
        backup-uri (str "file:" (fs/absolutize (fs/path  "datomic/backups" db-name)))]
    (run (format "datomic-run --main datomic backup-db %s %s" db-uri backup-uri))))

(defn restore [{{:keys [db-name]} :opts}]
  (db-name-req db-name)
  (when (running?)
    (info "Stop transactor before running restore")
    (System/exit 1))
  (info "Restoring" db-name "to" (str "./datomic/backups/" db-name))
  (let [db-uri     (datomic-bin-relative-db-uri db-name)
        backup-uri (str "file:" (fs/absolutize (fs/path  "datomic/backups" db-name)))]
    (run (format "datomic-run --main restore-db %s %s" backup-uri db-uri))))

;; CLI

(def help-info (partial log :blue))
(def help-command (partial log :olive))

(defn help [& _args]
  (println (bling/bling [:bold.green "Datomic Pro Manager (DPM)"]))
  (help-info "Datomic docs:" "https://docs.datomic.com")
  (help-info "Datomic Peer API docs:" "https://docs.datomic.com/clojure/index.html")
  (help-info "DPM docs:" "https://github.com/filipesilva/datomic-pro-manager")
  (help-info "Datomic Pro Version:" (get-datomic-version))
  (help-info "Running:" (running?))
  (help-info "DB URI:" (:datomic-db-uri config))
  (help-info "Deps:")
  (println (format "  com.datomic/peer          {:mvn/version \"%s\"}" (get-datomic-version)))
  (cond
    sqlite?
    (println (format "  org.xerial/sqlite-jdbc    {:mvn/version \"%s\"}" sqlite-version))

    postgresql?
    (println (format "  org.postgresql/postgresql {:mvn/version \"%s\"}" postgresql-version)))
  (help-info "Create a DB called 'app' and connect to it:")
  (println (format
            "  (require '[datomic.api :as d])
  (def db-uri \"%s\")
  (d/create-database db-uri)
  (def conn (d/connect db-uri))
  (d/db-stats (d/db conn))
  ;; {:datoms 268 ,,,}"
            (str/replace (:datomic-db-uri config) "{db-name}" "app")))
  (help-info "Available commands:")
  (help-command "  up           " "run datomic, setting it up if needed")
  (help-command "  test         " "test connectivity")
  (help-command "  download     " "download datomic pro")
  (help-command "  clean        " "remove datomic pro config dir")
  (help-command "  console      " "start datomic console")
  (help-command "  backup <db>  " "backup db to ./backups/db")
  (help-command "  restore <db> " "restore db from ./backups/db")
  (when sqlite?
    (help-command "  sqlite create" "create sqlite db at ./datomic/storage")
    (help-command "  sqlite delete" "delete sqlite db at ./datomic/storage")))

(def commands
  [{:cmds [] :fn help}
   {:cmds ["up"] :fn up}
   {:cmds ["test"] :fn test}
   {:cmds ["clean"] :fn datomic-delete}
   {:cmds ["console"] :fn console}
   {:cmds ["backup"] :fn backup, :args->opts [:db-name]}
   {:cmds ["restore"] :fn restore, :args->opts [:db-name]}
   {:cmds ["sqlite" "create"] :fn sqlite-create}
   {:cmds ["sqlite" "delete"] :fn sqlite-delete}])

(defn -main
  [_]
  (cli/dispatch commands *command-line-args*))
