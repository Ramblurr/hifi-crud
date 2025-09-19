(ns hifi.assets.html
  (:require
   [hifi.assets :as assets]
   [hifi.html.protocols :as html.p]))

(defn integrity? [attrs]
  (some? (:integrity attrs)))

(defn with-integrity [{:keys [href src crossorigin] :as attrs} asset-ctx]
  (assoc attrs :integrity (assets/asset-integrity asset-ctx (or src href))
         :crossorigin (or crossorigin "anonymous")))

(defn rewrite-stylesheet [asset-ctx [_ {:keys [href] :as attrs} & content]]
  (let [digest-path (assets/asset-path asset-ctx href)]
    [:link (cond-> (assoc attrs
                          :href digest-path
                          :rel "stylesheet")
             (integrity? attrs) (with-integrity asset-ctx))
     content]))

(defn rewrite-preload [asset-ctx [_ {:keys [href] :as attrs} & content]]
  (let [digest-path (assets/asset-path asset-ctx href)]
    [:link (cond-> (assoc attrs
                          :href digest-path
                          :rel "preload")
             (integrity? attrs) (with-integrity asset-ctx))
     content]))

(defn rewrite-javascript [asset-ctx [_ {:keys [src] :as attrs} & content]]
  (let [digest-path (assets/asset-path asset-ctx src)]
    [:script (cond-> (assoc attrs :src digest-path)
               (integrity? attrs) (with-integrity asset-ctx))
     content]))

(defn rewrite-image [asset-ctx [tag attrs & content]]
  (let [logical-path (:src attrs)
        digest-path (assets/asset-path asset-ctx logical-path)]
    [tag (assoc attrs :src digest-path) content]))

(defn rewrite-audio [asset-ctx [tag attrs & content]]
  (let [logical-path (:src attrs)
        digest-path (assets/asset-path asset-ctx logical-path)]
    [tag (assoc attrs :src digest-path) content]))

(defrecord ManifestElementProcessor [asset-ctx]
  html.p/AssetElementProcessor
  (rewrite-asset-element [_ el]
    (let [metadata (meta el)]
      (if (::html.p/processed? metadata)
        el
        (let [asset-type   (-> metadata :hifi.html/asset-marker :type)
              processed-el (case asset-type
                             :hifi.html/stylesheet (rewrite-stylesheet asset-ctx el)
                             :hifi.html/preload    (rewrite-preload asset-ctx el)
                             :hifi.html/javascript (rewrite-javascript asset-ctx el)
                             :hifi.html/image      (rewrite-image asset-ctx el)
                             :hifi.html/audio      (rewrite-audio asset-ctx el)
                             ;; Unknown type - pass through unchanged
                             el)]
          (with-meta processed-el
            (assoc metadata ::html.p/processed? true)))))))

(defn manifest-element-processor
  "Creates a ManifestElementProcessor that rewrites asset elements using the provided asset context.

  The processor handles the following asset types:
  - :hifi.html/stylesheet - Rewrites <link> stylesheet tags
  - :hifi.html/preload - Rewrites <link> preload tags
  - :hifi.html/javascript - Rewrites <script> tags
  - :hifi.html/image - Rewrites <img> tags
  - :hifi.html/audio - Rewrites <audio> tags

  Arguments:
  - asset-ctx: The asset context containing manifest and configuration"
  [asset-ctx]
  (->ManifestElementProcessor asset-ctx))
