(ns build
  (:refer-clojure :exclude [test])
  (:require
   [babashka.process :as p]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(def project (-> (edn/read-string (slurp "deps.edn")) :aliases :neil :project))
(def rev (str/trim (b/git-process {:git-args "rev-parse HEAD"})))
(def lib (:name project))
(def version (:version project))
(def main (:main project))
(def license-id (-> project :license :id))
(def license-file (or (-> project :license :file) "LICENSE"))
(def description (:description project))
(def repo-url-prefix (:url project (-> (b/git-process {:git-args "remote get-url origin"})
                                       (str/trim)
                                       (str/replace #"\.git$" ""))))
(assert (symbol? lib) ":name must be set in deps.edn under the :neil alias")
(assert (string? version) ":version must be set in deps.edn under the :neil alias")
(assert (symbol? main) ":main must be set in deps.edn under the :neil alias")
(assert (string? description) ":description must be set in deps.edn under the :neil alias")
(assert (string? license-id) "[:license :id] must be set in deps.edn under the :neil alias")

(def class-dir "target/classes")
(def basis_ (delay (b/create-basis {:project "deps.edn"})))

(defn permalink [subpath]
  (str repo-url-prefix "/blob/" rev "/" subpath))

(defn url->scm [url-string]
  (let [[_ domain repo-path] (re-find #"https?://?([\w\-\.]+)/(.+)" url-string)]
    [:scm
     [:url (str "https://" domain "/" repo-path)]
     [:connection (str "scm:git:https://" domain "/" repo-path)]
     [:developerConnection (str "scm:git:ssh:git@" domain ":" repo-path)]]))

(defn- build-opts [opts]
  (assoc opts
         :lib lib
         :main main
         :uber-file (format "target/uber-%s-%s.jar" (name lib) version)
         :jar-file (format "target/%s-%s.jar" (name lib) version)
         :java-opts ["--enable-native-access=ALL-UNNAMED"]
         :basis @basis_
         :class-dir class-dir
         :src-dirs ["src"]
         :binary "target/hifi"
         :ns-compile [main]
         :version version
         :pom-data [[:description description]
                    [:url repo-url-prefix]
                    [:licenses
                     [:license
                      [:name license-id]
                      [:url (permalink license-file)]]]
                    (conj (url->scm repo-url-prefix) [:tag rev])]))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [opts]
  (let [opts (build-opts opts)]
    (b/write-pom opts)
    (b/copy-dir {:src-dirs ["src" "resources"]
                 :target-dir class-dir})
    (b/jar opts)))

(defn can-build-native-image? []
  (let [os-windows? (.startsWith (System/getProperty "os.name") "Windows")
        proc        (if os-windows? (p/sh "where" "native-image.cmd") (p/sh "which" "native-image"))]
    (= 0 (:exit proc))))

(def graal-version (memoize (fn [] (:out (p/shell {:out :string} "native-image" "--version")))))

(defn community-edition? []
  (some? (re-find #"GraallVM CE" (graal-version))))

(defn oracle-edition? []
  (some? (re-find #"Oracle GraalVM" (graal-version))))

(defn native-image [opts]
  (when-not (can-build-native-image?)
    (println "native-image not found. Verify GraalVM is installed and on the PATH.")
    (System/exit 1))
  (println "\nCompiling to native image with GraalVM...")
  (let [{jar-path :uber-file bin-path :binary} (build-opts opts)]
    (try
      (p/shell (cond-> ["native-image"
                        "-jar" jar-path
                        bin-path
                        "-H:+ReportExceptionStackTraces"
                        "--features=clj_easy.graal_build_time.InitClojureClasses"
                        "--verbose"
                        "--future-defaults=all"
                        "--no-fallback"]
                 (oracle-edition?) (conj "--emit build-report")))

      (catch Exception _
        (System/exit 1)))))

(defn ci
  "Run the CI pipeline (tests, compile, jar, native image" [opts]
  #_(test opts)
  (clean nil)
  (let [opts (build-opts opts)]
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src"] :target-dir class-dir})
    (println (str "\nCompiling " main "..."))
    (b/compile-clj opts)
    (println "\nBuilding JAR...")
    (jar opts)
    (println "\nBuilding Uber JAR...")
    (b/uber opts)
    (native-image opts))
  opts)
