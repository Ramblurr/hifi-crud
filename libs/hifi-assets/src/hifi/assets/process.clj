;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns ^:no-doc hifi.assets.process
  "Asset processing"
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]))

(defn resolve-path
  "Resolves a filename relative to an asset's logical directory.
   Handles ../, ./, and / prefixes. Bare paths are treated as root-relative."
  [asset-logical-dir filename]
  (cond
    ;; Root-relative paths start with /
    (str/starts-with? filename "/")
    (subs filename 1) ; remove leading slash for root-relative within load paths

    ;; All other paths need normalization
    :else
    (let [base-dir       (if (= asset-logical-dir ".") "" asset-logical-dir)
          combined-path  (if (empty? base-dir)
                           filename
                           (fs/path base-dir filename))
          normalized     (fs/normalize combined-path)
          normalized-str (str normalized)]
      ;; If normalized path starts with ../ and we're at root, remove the ../
      (if (and (= asset-logical-dir ".") (str/starts-with? normalized-str "../"))
        (str/replace normalized-str #"^\.\./" "")
        normalized-str))))

(def default-ext->mime
  {".css"         "text/css"
   ".js"          "application/javascript"
   ".json"        "application/json"
   ".edn"         "application/edn"
   ".clj"         "application/clojure"
   ".cljc"        "application/clojure"
   ".cljs"        "application/clojurescript"
   ".conf"        "text/plain"
   ".jpg"         "image/jpeg"
   ".jpeg"        "image/jpeg"
   ".gif"         "image/gif"
   ".png"         "image/png"
   ".avi"         "video/avi"
   ".html"        "text/html"
   ".htm"         "text/html"
   ".txt"         "text/plain"
   ".svg"         "image/svg+xml"
   ".webp"        "image/webp"
   ".ico"         "image/x-icon"
   ".bmp"         "image/bmp"
   ".tiff"        "image/tiff"
   ".tif"         "image/tiff"
   ".mp4"         "video/mp4"
   ".webm"        "video/webm"
   ".ogv"         "video/ogg"
   ".mov"         "video/quicktime"
   ".wav"         "audio/wav"
   ".ogg"         "audio/ogg"
   ".m4a"         "audio/mp4"
   ".aac"         "audio/aac"
   ".flac"        "audio/flac"
   ".weba"        "audio/webm"
   ".mp3"         "audio/mpeg"
   ".woff"        "font/woff"
   ".woff2"       "font/woff2"
   ".ttf"         "font/ttf"
   ".otf"         "font/otf"
   ".eot"         "application/vnd.ms-fontobject"
   ".pdf"         "application/pdf"
   ".xml"         "application/xml"
   ".csv"         "text/csv"
   ".md"          "text/markdown"
   ".mjs"         "application/javascript"
   ".jsx"         "application/javascript"
   ".ts"          "application/typescript"
   ".tsx"         "application/typescript"
   ".map"         "application/json"
   ".wasm"        "application/wasm"
   ".scss"        "text/x-scss"
   ".sass"        "text/x-sass"
   ".less"        "text/x-less"
   ".yaml"        "application/yaml"
   ".yml"         "application/yaml"
   ".toml"        "application/toml"
   ".webmanifest" "application/manifest+json"
   ".zip"         "application/zip"
   ".gz"          "application/gzip"
   ".tar"         "application/x-tar"
   ".7z"          "application/x-7z-compressed"})

(defn infer-mime [ext->mime logical-path]
  (or (some (fn [[ext mime]] (when (str/ends-with? logical-path ext) mime)) ext->mime)
      "application/octet-stream"))

(defn load-asset
  "Read file content and add :content and :mime-type. Emits a warning on failure."
  [ext->mime {:keys [abs-path logical-path] :as asset}]
  (try
    {:asset    (-> asset
                   (assoc :content (slurp abs-path))
                   (assoc :mime-type (infer-mime ext->mime logical-path)))
     :warnings []}
    (catch Throwable t
      {:asset    asset
       :warnings [(str "Failed to read " abs-path ": " (.getMessage t))]})))

(defn applicable-processors
  "Select processors whose :mime-types contains asset's :mime-type.
   :mime-types can be a set or seq."
  [processors {:keys [mime-type]}]
  (let [matches? (fn [coll x]
                   (cond
                     (set? coll)        (contains? coll x)
                     (sequential? coll) (some #{x} coll)
                     :else              false))]
    (filter #(matches? (:mime-types %) mime-type) processors)))

(defn run-processors
  "Sequentially apply processors. Each processor is {:process (fn [ctx asset content] -> {:content .. :warnings [...]})}."
  [ctx asset processors initial-content]
  (reduce
   (fn [{:keys [content warnings]} {:keys [process]}]
     (let [{:keys [content] :as result} (process ctx asset content)]
       {:content  content
        :warnings (into (vec warnings) (:warnings result))}))
   {:content initial-content :warnings []}
   processors))

(defn process-one
  "Process a single asset. Returns {:asset <updated-asset> :warnings [...]}."
  [{:keys [ext->mime] :as ctx
    :or   {ext->mime default-ext->mime}} processors asset]
  (let [{loaded :asset read-warnings :warnings} (load-asset ext->mime asset)]
    (if-not (:content loaded)
      {:asset loaded :warnings read-warnings}
      (let [procs (applicable-processors processors loaded)
            {:keys [content warnings]}
            (run-processors ctx loaded procs (:content loaded))]
        {:asset    (assoc loaded :processed-content content)
         :warnings (into (vec read-warnings) warnings)}))))

;; --- public API ------------------------------------------------------------

(defn process-assets
  "ctx expects :config {:hifi.assets/processors [...]}. Returns {:assets [...] :warnings [...] }."
  [ctx assets]
  (let [processors (get-in ctx [:config :hifi.assets/processors] [])
        results    (map #(process-one ctx processors %) assets)]
    {:assets   (mapv :asset results)
     :warnings (->> results (mapcat :warnings) vec)}))
