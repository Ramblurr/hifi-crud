;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.importmap.package
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [bling.core :as bling]
   [cheshire.core :as cheshire]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [fipp.edn :as fipp]
   [hifi.dev-tasks.util :as util]
   [medley.core :as medley]))

(defn ->json [& opts]
  (apply cheshire/generate-string opts))

(defn json-> [s & {:keys [key-fn]}]
  (cheshire/parse-string s (or key-fn keyword)))

(def endpoint "https://api.jspm.io/generate")

(defn request-opts [{:keys [env from]
                     :or   {env  "production"
                            from "jspm.io"}}]
  {:env  env
   :from from})

(defn import-request [packages {:keys [env from]}]
  {:uri     endpoint
   :headers {"Accept"       "application/json"
             "Content-Type" "application/json"}
   :method  :post
   :throw   false
   :body    (->json {:flattenScope true
                     :install      packages
                     :env          ["browser" "module" env]
                     :provider     from})})

(defn extract-imports [{:keys [body]}]
  (-> body
      (json-> {:key-fn str})
      (get-in ["map" "imports"])))

(defn handle-failure-response [{:keys [status body]}]
  (if-let [error (try
                   (:error (json-> body))
                   (catch Exception e
                     nil))]
    (util/error "[importmap] " error)
    (util/error "[importmap] Unexpected response status" status))
  nil)

(defn fetch-imports! [opts package]
  (let [import-opts                    (request-opts opts)
        {:keys [status body] :as resp} (http/request (import-request package import-opts))]

    (condp contains? status
      #{200}     (extract-imports resp)
      #{404 401} (do
                   (util/error "[importmap] Couldn't find any packages for" package "on" (:from import-opts))
                   nil)
      (handle-failure-response resp))))

(defn pkg-filename [pkg]
  (str (str/replace pkg #"/" "--") ".js"))

(defn vendored-pkg-path [{:keys [vendor-path]} pkg]
  (str
   (fs/path vendor-path (pkg-filename pkg))))

(defn ensure-vendor-dir! [{:keys [vendor-path]}]
  (fs/create-dirs vendor-path))

(defn remove-existing-pkg! [opts pkg]
  (let [path (vendored-pkg-path opts pkg)]
    (when (fs/exists? path)
      (do
        (util/debug "[importmap] removing existing pkg at" path)
        (fs/delete-tree path)))))

(defn remove-sourcemap-comment [source]
  (str/replace source #"(//\# sourceMappingURL=.*)" ""))

(defn extract-version [url]
  (subs
   (re-find #"@\d+\.\d+\.\d+" url)
   1))

(defn save-vendored-pkg! [opts pkg url source]
  (let [path (vendored-pkg-path opts pkg)]
    (util/debug "[importmap] writing" path)
    (spit path
          (str
           (format "// %s%s downloaded from %s\n\n" pkg (extract-version url) url)
           (remove-sourcemap-comment source)))))

(defn download-pkg-file! [opts pkg url]
  (util/debug "[importmap] fetching" url)
  (let [{:keys [status] :as resp} (http/request {:uri    url
                                                 :method :get
                                                 :throw  false})]
    (if (= 200 status)
      (save-vendored-pkg! opts pkg url (:body resp))
      (handle-failure-response resp))))

(defn download-pkg! [opts pkg url]
  (ensure-vendor-dir! opts)
  (remove-existing-pkg! opts pkg)
  (download-pkg-file! opts pkg url))

(defn vendored-pin [{:keys [preloads]} pkg url]
  (let [pkg-filename (pkg-filename pkg)
        _            (prn pkg pkg-filename)
        version      (extract-version url)
        preloads     (if (string? preloads)
                       [preloads]
                       preloads)
        pin          (if (= pkg-filename (str pkg ".js"))
                       {:version version}
                       {:version version
                        :to      pkg-filename})]
    (if (nil? preloads)
      pin
      (assoc pin :preloads pin))))

(defn read-importmap [{:keys [importmap-path]}]
  (if (fs/exists? importmap-path)
    (edn/read-string (slurp importmap-path))
    {}))

(defn packaged? [im pkg]
  (get-in im [:pins pkg]))

(defn update-pin [im pkg pin]
  (assoc-in im [:pins pkg] pin))

(defn process-import! [opts [pkg url]]
  (let [pin (vendored-pin opts pkg url)]
    (util/info (bling/bling "Pinning package " [:italic pkg] " to " [:italic (pkg-filename pkg)] ".js via download from " [:italic url]))
    (download-pkg! opts pkg url)
    [pkg pin]))

(defn pin-imports [im [pkg pin]]
  (update-pin im pkg pin))

(defn remove-pins [im pkg]
  (update im :pins dissoc pkg))

(defn write-importmap! [{:keys [importmap-path]} new-im]
  (fs/create-dirs (fs/parent importmap-path))
  (spit importmap-path (with-out-str (fipp/pprint new-im))))

(defn pin-packages [{:keys [packages] :as opts}]
  (let [im     (read-importmap opts)
        result (->> packages
                    (mapcat (partial fetch-imports! opts))
                    (map (partial process-import! opts))
                    (reduce pin-imports im)
                    (doall))]
    (write-importmap! opts result)))

(defn unpin-packages [{:keys [packages] :as opts}]
  (let [im     (read-importmap opts)
        result (->> packages
                    (mapcat (partial fetch-imports! opts))
                    (map first)
                    (filter (fn [pkg] (packaged? im pkg)))
                    (map (fn [pkg]
                           (util/info (bling/bling "Unpinning and removing packages " [:italic pkg]))
                           (remove-existing-pkg! opts pkg)
                           pkg))
                    (reduce remove-pins im)
                    (doall))]
    (write-importmap! opts result)))

(defn package-versions [im]
  (map (fn [[pkg {:keys [version]}]]
         [pkg version])
       (:pins im)))

(defn to-json-importmap [{:keys [assets-route] :as opts}]
  (->json
   {:imports
    (medley/map-kv-vals (fn [name {:keys [to]}]
                          (if to
                            (str assets-route "/" to)
                            (str assets-route "/" name ".js")))
                        (:pins (read-importmap opts)))}
   {:pretty true}))
