(ns hifi.assets.processors
  (:require
   [hifi.assets.process :refer [resolve-path]]
   [babashka.fs :as fs]
   [clojure.string :as str]))

(def css-url-pattern
  "CSS url() pattern that excludes external URLs, data URLs, and captures fragments separately"
  #"url\(\s*[\"']?(?!(?:#|%23|data:|http:|https:|//))([^\"'#)\s]+)([#?][^\"')]+)?\s*[\"']?\)")

(def js-asset-pattern
  "JavaScript HIFI_ASSET_URL() pattern"
  #"HIFI_ASSET_URL\(\s*[\"']?(?!(?:#|%23|data:|http:|https:|//))([^\"'#)\s]+)([#?][^\"')]+)?\s*[\"']?\)")

(def source-mapping-pattern
  "Source mapping URL pattern matching sourceMappingURL comments at end of file.
   Captures: comment-start, map-path, comment-end (optional)"
  #"(//|/\*)#\s*sourceMappingURL=([^)\s]+\.map)(\s*?\*/)?[\s]*\Z")

(defn css-dependencies
  "Finds all asset dependencies in CSS content via url() references."
  [_ctx asset content]
  (let [asset-dir (or (some-> (:logical-path asset) fs/parent str) ".")]
    (->> (re-seq css-url-pattern content)
         (map (fn [[_ path _fragment]]
                (resolve-path asset-dir path)))
         set)))

(defn js-dependencies
  "Finds all asset dependencies in JavaScript content via HIFI_ASSET_URL() references."
  [_ctx asset content]
  (let [asset-dir (or (some-> (:logical-path asset) fs/parent str) ".")]
    (->> (re-seq js-asset-pattern content)
         (map (fn [[_ path _fragment]]
                (resolve-path asset-dir path)))
         set)))

(defn source-mapping-dependencies
  "Finds source mapping dependencies in JavaScript/CSS content."
  [_ctx asset content]
  (let [asset-dir (or (some-> (:logical-path asset) fs/parent str) ".")]
    (->> (re-seq source-mapping-pattern content)
         (map (fn [[_ _comment-start map-path _comment-end]]
                (resolve-path asset-dir map-path)))
         set)))

(defn css-processor
  "Processes CSS content, replacing url() references with digested paths.
   Returns {:warnings [] :content processed-content-string}."
  [ctx asset content]
  (let [asset-dir (or (some-> (:logical-path asset) fs/parent str) ".")
        prefix    (get-in ctx [:config :hifi.assets/prefix])
        manifest  (:manifest ctx)
        matches   (re-seq css-url-pattern content)
        warnings  (vec (keep (fn [[_full-match path _fragment]]
                               (let [resolved-path (resolve-path asset-dir path)]
                                 (when-not (get manifest resolved-path)
                                   {:type          :missing-asset
                                    :asset         (:logical-path asset)
                                    :missing-path  resolved-path
                                    :original-path path})))
                             matches))
        processed-content
        (str/replace content css-url-pattern
                     (fn [match]
                       (let [[_full-match path fragment] match
                             resolved-path               (resolve-path asset-dir path)]
                         (if-let [digest-info (get manifest resolved-path)]
                           (format "url(\"%s/%s%s\")"
                                   prefix
                                   (:digest-path digest-info)
                                   (or fragment ""))
                           (format "url(\"%s\")" path)))))]
    {:warnings warnings
     :content  processed-content}))

(defn js-processor
  "Processes JavaScript content, replacing HIFI_ASSET_URL() references with digested paths.
   Returns {:warnings [] :content processed-content-string}."
  [ctx asset content]
  (let [asset-dir (or (some-> (:logical-path asset) fs/parent str) ".")
        prefix    (get-in ctx [:config :hifi.assets/prefix])
        manifest  (:manifest ctx)
        matches   (re-seq js-asset-pattern content)
        warnings  (vec (keep (fn [[_full-match path _fragment]]
                               (let [resolved-path (resolve-path asset-dir path)]
                                 (when-not (get manifest resolved-path)
                                   {:type          :missing-asset
                                    :asset         (:logical-path asset)
                                    :missing-path  resolved-path
                                    :original-path path})))
                             matches))
        processed-content
        (str/replace content js-asset-pattern
                     (fn [match]
                       (let [[_full-match path fragment] match
                             resolved-path               (resolve-path asset-dir path)]
                         (if-let [digest-info (get manifest resolved-path)]
                           (format "\"%s/%s%s\""
                                   prefix
                                   (:digest-path digest-info)
                                   (or fragment ""))
                           (format "\"%s\"" path)))))]
    {:warnings warnings
     :content  processed-content}))

(defn source-mapping-processor
  "Processes sourceMappingURL comments, replacing with digested paths or removing if missing.
   Returns {:warnings [] :content processed-content-string}."
  [ctx asset content]
  (let [asset-dir (or (some-> (:logical-path asset) fs/parent str) ".")
        prefix    (get-in ctx [:config :hifi.assets/prefix])
        manifest  (:manifest ctx)
        matches   (re-seq source-mapping-pattern content)
        warnings  (vec (keep (fn [[_full-match _comment-start map-path _comment-end]]
                               (let [resolved-path (resolve-path asset-dir map-path)]
                                 (when-not (get manifest resolved-path)
                                   {:type          :missing-source-map
                                    :asset         (:logical-path asset)
                                    :missing-path  resolved-path
                                    :original-path map-path})))
                             matches))
        processed-content
        (str/replace content source-mapping-pattern
                     (fn [match]
                       (let [[_full-match comment-start map-path comment-end] match
                             resolved-path                                    (resolve-path asset-dir map-path)]
                         (if-let [digest-info (get manifest resolved-path)]
                           (format "%s# sourceMappingURL=%s/%s%s"
                                   comment-start
                                   prefix
                                   (:digest-path digest-info)
                                   (or comment-end ""))
                           ;; Remove sourceMappingURL comment but preserve comment structure
                           (format "%s%s" comment-start (or comment-end ""))))))]
    {:warnings warnings
     :content  processed-content}))

(def default-processors
  [{:mime-types   #{"text/css"}
    :dependencies css-dependencies
    :process      css-processor}
   {:mime-types   #{"application/javascript"}
    :dependencies js-dependencies
    :process      js-processor}
   {:mime-types   #{"text/css" "application/javascript"}
    :dependencies source-mapping-dependencies
    :process      source-mapping-processor}])
