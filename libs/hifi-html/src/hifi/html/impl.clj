(ns hifi.html.impl
  (:require
   [clojure.string]
   [clojure.walk :as walk]
   [dev.onionpancakes.chassis.core :as chassis]
   [hifi.html.protocols :as p]
   [hifi.html.util :as u]))

(defn has-asset-marker? [node]
  (and
   (vector? node)
   (keyword? (first node))
   (-> node meta :hifi.html/asset-marker some?)))

(defn collect-link
  [[tag {:keys [href src rel as type crossorigin integrity]} _]]
  (let [logical-path (or href src)
        integrity? (some? integrity)]
    (when logical-path
      (cond
        (= tag :link)
        (when (#{"preload" "modulepreload"} rel)
          (cond-> {:path href :rel rel}
            (and (= rel "preload") as) (assoc :as as)
            type (assoc :type type)
            crossorigin (assoc :crossorigin crossorigin)
            integrity? (assoc :integrity? true)))

        (= tag :script)
        (let [module? (= "module" type)
              rel* (if module? "modulepreload" "preload")]
          (cond-> {:path href :rel rel*}
            (not module?) (assoc :as "script")
            (and (not module?) (some? type)) (assoc :type type)
            crossorigin (assoc :crossorigin crossorigin)
            integrity? (assoc :integrity? true)))))))

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
  [{:hifi/keys [assets] :as _ctx} hiccup]
  (if assets
    (walk/prewalk (fn [node]
                    (if (has-asset-marker? node)
                      (p/rewrite-asset-element assets node)
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
                            (let [logical-path (:path preload)
                                  resolved-path (p/resolve-path asset-resolver logical-path)
                                  integrity (p/integrity asset-resolver logical-path)]
                              (when resolved-path
                                (cond-> (assoc preload :path resolved-path)
                                  integrity (assoc :integrity integrity))))))
                     (filter some?))
               conj
               []
               [hiccup])
    []))

(defn render
  ([{:keys [asset-resolver] :as ctx} hiccup {:as _opts
                                             :keys [collect-preloads?
                                                    delay-render?]
                                             :or {collect-preloads? true
                                                  delay-render? false}}]
   {:preloads (when collect-preloads?
                (resolve-preloads asset-resolver hiccup))
    :html (when-not delay-render? (->str ctx hiccup))
    :render_ (when delay-render?
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
             :or {max-size 1000}}]
  (when (seq preloads)
    (let [sb (StringBuilder.)]
      (loop [remaining preloads
             first? true]
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
