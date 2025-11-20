(ns datastar
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]))

(defn get-latest-release []
  (let [resp (http/get "https://api.github.com/repos/starfederation/datastar/releases/latest"
                       {:as :string})
        data (json/parse-string (:body resp) true)]
    (:tag_name data)))

(defn extract-version [tag]
  (str/replace tag #"^v" ""))

(defn find-local-version []
  (let [files (fs/list-dir "libs/hifi-datastar/resources/hifi-datastar")
        pattern #"datastar@(.+)\.js$"]
    (some (fn [f]
            (let [fname (fs/file-name f)
                  m (re-matches pattern (str fname))]
              (when m (second m))))
          files)))

(defn update-clojure-file [version]
  (let [clj-file "libs/hifi-datastar/src/hifi/datastar.clj"]
    (fs/update-file clj-file
                    (fn [content]
                      (str/replace content
                                   #":resource-path \"hifi-datastar/datastar@[^\"]+\.js\""
                                   (str ":resource-path \"hifi-datastar/datastar@" version ".js\""))))
    (println (str "Updated: " clj-file))))

(defn download-and-update [version]
  (let [base-url (str "https://cdn.jsdelivr.net/gh/starfederation/datastar@" version "/bundles")
        js-url (str base-url "/datastar.js")
        map-url (str base-url "/datastar.js.map")
        js-resp (http/get js-url {:as :string})
        map-resp (http/get map-url {:as :string})
        js-content (:body js-resp)
        map-content (:body map-resp)
        updated-js (str/replace js-content
                                #"//# sourceMappingURL=datastar\.js\.map"
                                (str "//# sourceMappingURL=datastar@" version ".js.map"))
        js-path (str "libs/hifi-datastar/resources/hifi-datastar/datastar@" version ".js")
        map-path (str "libs/hifi-datastar/resources/hifi-datastar/datastar@" version ".js.map")]
    (fs/write-bytes js-path (.getBytes updated-js "utf-8"))
    (fs/write-bytes map-path (.getBytes map-content "utf-8"))
    (println (str "Updated datastar to " version))
    (println (str "Downloaded: " js-path))
    (println (str "Downloaded: " map-path))
    (update-clojure-file version)))

(defn update-datastar []
  (let [latest-tag (get-latest-release)
        latest-version (extract-version latest-tag)
        local-version (find-local-version)]
    (println (str "Latest version: " latest-version))
    (println (str "Local version: " local-version))
    (if (= latest-version local-version)
      (println "Already at latest version!")
      (download-and-update latest-version))))
