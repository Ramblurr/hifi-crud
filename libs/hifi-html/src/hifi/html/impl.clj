(ns ^:no-doc hifi.html.impl
  (:require
   [clojure.string]
   [clojure.walk :as walk]
   [dev.onionpancakes.chassis.core :as chassis]
   [hifi.html.protocols :as p]
   [hifi.html.util :as u]))

(defn chassis-data?
  [x]
  (or (:dev.onionpancakes.chassis.core/content (meta x))
      (= (type x) dev.onionpancakes.chassis.core.RawString)))

(def asset-tags #{:hifi.html/stylesheet-link
                  :hifi.html/preload-link
                  :hifi.html/javascript-include
                  :hifi.html/image
                  :hifi.html/audio})

;; TODO future tags: video, picture, don't forget to update hifi.html.impl/asset-tags
(defmethod chassis/resolve-alias ::stylesheet-link
  [_ attrs content]
  (with-meta
    [:link attrs content]
    {::asset-marker {:type ::stylesheet-link}}))

(defmethod chassis/resolve-alias ::preload-link
  [_ attrs content]
  (with-meta
    [:link attrs content]
    {::asset-marker {:type ::preload-link}}))

(defmethod chassis/resolve-alias ::javascript-include
  [_ attrs content]
  (with-meta
    [:script attrs content]
    {::asset-marker {:type ::javascript-include}}))

(defmethod chassis/resolve-alias ::image
  [_ attrs content]
  (with-meta
    [:img attrs content]
    {::asset-marker {:type ::image :opts attrs}}))

(defmethod chassis/resolve-alias ::audio
  [_ attrs content]
  (with-meta
    [:audio attrs content]
    {::asset-marker {:type ::audio :opts attrs}}))

(defn integrity? [attrs]
  (some? (:integrity attrs)))

(defn with-integrity [{:keys [href src crossorigin] :as attrs} asset-resolver]
  (assoc attrs :integrity (p/integrity asset-resolver (or src href))
         :crossorigin (or crossorigin "anonymous")))

(defn rewrite-stylesheet [asset-resolver [_ {:keys [href] :as attrs} & content]]
  (let [digest-path (p/resolve-path asset-resolver href)]
    [:link (cond-> (assoc attrs
                          :href digest-path
                          :rel "stylesheet")
             (integrity? attrs) (with-integrity asset-resolver))
     content]))

(defn rewrite-preload [asset-resolver [_ {:keys [href] :as attrs} & content]]
  (let [digest-path (p/resolve-path asset-resolver href)]
    [:link (cond-> (assoc attrs
                          :href digest-path
                          :rel "preload")
             (integrity? attrs) (with-integrity asset-resolver))
     content]))

(defn rewrite-javascript [asset-resolver [_ {:keys [src] :as attrs} & content]]
  (let [digest-path (p/resolve-path asset-resolver src)]
    [:script (cond-> (assoc attrs :src digest-path)
               (integrity? attrs) (with-integrity asset-resolver))
     content]))

(defn rewrite-image [asset-resolver [tag attrs & content]]
  (let [logical-path (:src attrs)
        digest-path  (p/resolve-path asset-resolver logical-path)]
    [tag (assoc attrs :src digest-path) content]))

(defn rewrite-audio [asset-resolver [tag attrs & content]]
  (let [logical-path (:src attrs)
        digest-path  (p/resolve-path asset-resolver logical-path)]
    [tag (assoc attrs :src digest-path) content]))

(defn rewrite-asset-element [asset-resolver el]
  (let [metadata (meta el)]
    (if (::p/processed? metadata)
      el
      (let [asset-type   (or (-> metadata :hifi.html/asset-marker :type)
                             (first el))
            processed-el (case asset-type
                           :hifi.html/stylesheet-link    (rewrite-stylesheet asset-resolver el)
                           :hifi.html/preload-link       (rewrite-preload asset-resolver el)
                           :hifi.html/javascript-include (rewrite-javascript asset-resolver el)
                           :hifi.html/image              (rewrite-image asset-resolver el)
                           :hifi.html/audio              (rewrite-audio asset-resolver el)
                           el)]
        (with-meta processed-el
          (assoc metadata ::p/processed? true))))))

(defn asset-node? [node]
  (and
   (vector? node)
   (keyword? (first node))
   (or (-> node meta :hifi.html/asset-marker some?)
       (contains? asset-tags (first node)))))

(defn collect-link
  [[tag {:keys [href src rel as type crossorigin integrity]} _]]
  (let [logical-path (or href src)
        integrity?   (some? integrity)]
    (when logical-path
      (cond
        (= tag :link)
        (when (#{"preload" "modulepreload"} rel)
          (cond-> {:path href :rel rel}
            (and (= rel "preload") as) (assoc :as as)
            type                       (assoc :type type)
            crossorigin                (assoc :crossorigin crossorigin)
            integrity?                 (assoc :integrity? true)))

        (= tag :script)
        (let [module? (= "module" type)
              rel*    (if module? "modulepreload" "preload")]
          (cond-> {:path href :rel rel*}
            (not module?)                    (assoc :as "script")
            (and (not module?) (some? type)) (assoc :type type)
            crossorigin                      (assoc :crossorigin crossorigin)
            integrity?                       (assoc :integrity? true)))))))

(def collect-head-preloads-xf
  "Returns a transducer that extracts preloads from hiccup elements.

   Processes hiccup documents by finding <head> elements and extracting
   preload information from <link> and <script> tags within them."
  (comp (mapcat (fn [hiccup]
                  (if-let [head (u/find-first-element #(= :head (first %)) hiccup)]
                    (u/hiccup-seq head)
                    [])))
        (map collect-link)
        (filter some?)))

(defn process-asset-tags
  "Walks hiccup tree and resolves asset paths in the elements with asset metadata"
  [ctx hiccup]
  (if-let [asset-resolver (:hifi.assets/resolver ctx)]
    (walk/prewalk (fn [node]
                    (if (asset-node? node)
                      (rewrite-asset-element asset-resolver node)
                      node))
                  hiccup)
    hiccup))

(defn ->str
  ([ctx root]
   (->> root
        (process-asset-tags ctx)
        (chassis/html)))
  ([root]
   (->str nil root)))

(defn resolve-preloads
  "Extracts preloads from hiccup and resolves their asset paths. Returns a vector of resolved preloads."
  [asset-resolver hiccup]
  (if asset-resolver
    (transduce (comp collect-head-preloads-xf
                     (map (fn [preload]
                            (let [logical-path  (:path preload)
                                  resolved-path (p/resolve-path asset-resolver logical-path)
                                  integrity     (p/integrity asset-resolver logical-path)]
                              (when resolved-path
                                (cond-> (assoc preload :path resolved-path)
                                  integrity (assoc :integrity integrity))))))
                     (filter some?))
               conj
               []
               [hiccup])
    []))

(defn render
  ([ctx hiccup {:as   _opts
                :keys [collect-preloads?
                       delay-render?]
                :or   {collect-preloads? true
                       delay-render?     false}}]
   {:preloads (when collect-preloads?
                (resolve-preloads (:hifi.assets/resolver ctx) hiccup))
    :html     (when-not delay-render? (->str ctx hiccup))
    :render_  (when delay-render?
                (delay (->str ctx hiccup)))}))

(defn- append-link-to-builder!
  [^StringBuilder sb {:keys [href path rel as type crossorigin integrity]}]
  (let [url (or href path)]
    (.append sb "<")
    (.append sb url)
    (.append sb ">; rel=")
    (.append sb (or rel "preload"))
    (when as
      (.append sb "; as=")
      (.append sb as))
    (when type
      (.append sb "; type=\"")
      (.append sb type)
      (.append sb "\""))
    (when crossorigin
      (.append sb "; crossorigin=")
      (.append sb crossorigin))
    (when integrity
      (.append sb "; integrity=\"")
      (.append sb integrity)
      (.append sb "\""))))

(defn preloads->header
  [preloads {:keys [max-size]
             :or   {max-size 1000}}]
  (when (seq preloads)
    (let [sb (StringBuilder.)]
      (loop [remaining preloads
             first?    true]
        (if-let [preload (first remaining)]
          (let [start-length (.length sb)]
            (when-not first?
              (.append sb ", "))
            (append-link-to-builder! sb preload)
            (if (> (.length sb) max-size)
              (do
                (.delete sb start-length (.length sb))
                (when (pos? (.length sb))
                  (.toString sb)))
              (recur (rest remaining) false)))
          (when (pos? (.length sb))
            (.toString sb)))))))
