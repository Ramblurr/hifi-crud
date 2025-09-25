(ns hifi.assets.middleware
  "TODO: ns docs"
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [hifi.assets.impl :as assets]
   [hifi.assets.process :as process]
   [hifi.config :as config]
   [hifi.core :as h]
   [hifi.system.middleware :as h.mw])
  (:import
   (java.net URLDecoder)))

(defn- request-digest-path [prefix uri]
  (let [without-prefix (subs uri (count prefix))
        decoded        (URLDecoder/decode without-prefix "UTF-8")]
    (str/replace-first decoded #"^/+" "")))

(defn- find-manifest-entry [manifest requested-digest]
  (some (fn [[logical-path {:keys [digest-path] :as entry}]]
          (when (= requested-digest digest-path)
            (assoc entry :logical-path logical-path)))
        manifest))

(defn- extract-digest [digest-path]
  (when-let [filename (some-> digest-path fs/file-name str)]
    (some-> (re-find #"-([0-9a-zA-Z]{7,128})" filename)
            second)))

(defn- build-headers [{:keys [size digest-path last-modified]} logical-path]
  (let [etag         (when-let [digest (extract-digest digest-path)]
                       (str "\"" digest "\""))
        content-type (process/infer-mime process/default-ext->mime (str/lower-case logical-path))]
    (cond-> {"Content-Type" content-type
             "Cache-Control" "public, max-age=31536000, immutable"
             "Vary" "Accept-Encoding"}
      size          (assoc "Content-Length" (str size))
      etag          (assoc "ETag" etag)
      last-modified (assoc "Last-Modified" last-modified))))

(defn- asset-response [asset-ctx prefix request]
  (let [{:keys [manifest]} asset-ctx
        uri                (:uri request)
        method             (:request-method request)]
    (when (and uri (#{:get :head} method) (str/starts-with? uri prefix))
      (let [digest-path (request-digest-path prefix uri)
            entry       (find-manifest-entry manifest digest-path)]
        (when (and entry (= digest-path (:digest-path entry)))
          (let [logical-path (:logical-path entry)
                headers      (build-headers entry logical-path)
                etag         (get headers "ETag")
                request-hdrs (:headers request)
                inm          (or (get request-hdrs "if-none-match")
                                 (get request-hdrs "If-None-Match"))]
            (cond
              (and etag inm (= etag inm))
              {:status  304
               :headers (dissoc headers "Content-Length")
               :body    nil}

              (= method :head)
              {:status  200
               :headers headers
               :body    nil}

              :else
              (when-let [stream (assets/asset-read asset-ctx logical-path)]
                {:status  200
                 :headers headers
                 :body    stream}))))))))

(defn static-assets-middleware
  "Serves the assets from the static manifest"
  [opts]
  (let [asset-ctx (assets/create-asset-context opts)
        prefix    (get-in asset-ctx [:config :hifi.assets/prefix])]
    {:name ::assets
     :wrap (fn [handler]
             (fn
               ([request]
                (or (asset-response asset-ctx prefix request)
                    (handler request)))
               ([request respond raise]
                (if-let [resp (asset-response asset-ctx prefix request)]
                  (respond resp)
                  (handler request respond raise)))))}))

(defn dynamic-assets-middleware
  "Serves the assets from the manifest, reloading the manifest file on every request"
  [config]
  {:name ::assets
   :wrap (fn [handler]
           (fn
             ([request]
              (let [asset-ctx (assets/create-asset-context config)
                    prefix    (get-in asset-ctx [:config :hifi.assets/prefix])]
                (or (asset-response asset-ctx prefix request)
                    (handler request))))
             ([request respond raise]
              (let [asset-ctx (assets/create-asset-context config)
                    prefix    (get-in asset-ctx [:config :hifi.assets/prefix])
                    resp      (asset-response asset-ctx prefix request)]
                (if resp
                  (respond resp)
                  (handler request respond raise))))))})

(defn assets-middleware
  "Middleware that serves assets as configured by the :hifi/assets config key. "
  ([opts]
   (let [config (:hifi.assets/config opts)]
     (if (config/dev?)
       (dynamic-assets-middleware config)
       (static-assets-middleware config)))))

(def DynamicAssetsMiddlewareComponent
  (h.mw/middleware-component
   {:name                :hifi/assets-dynamic-resolve
    :factory             #(dynamic-assets-middleware %)
    :donut.system/config {:hifi.assets/config [:donut.system/ref [:hifi/assets :hifi.assets/config]]}}))

(def StaticAssetsMiddlewareComponent
  (h.mw/middleware-component
   {:name                :hifi/assets-static-resolve
    :factory             #(static-assets-middleware %)
    :donut.system/config {:hifi.assets/config [:donut.system/ref [:hifi/assets :hifi.assets/config]]}}))

(def AssetsMiddlewareComponent
  (h.mw/middleware-component
   {:name                :hifi/assets
    :factory             #(assets-middleware %)
    :donut.system/config {:hifi.assets/config [:donut.system/ref [:hifi/assets :hifi.assets/config]]}}))

(h/defcomponent AssetsResolverMiddlewareComponent
  "Plops the resolver middleware into the request map"
  (h.mw/middleware-component
   {:name                :hifi/assets-resolver
    :factory             (fn [config]
                           {:name :hifi/assets-resolver
                            :wrap (fn [handler]
                                    (fn [req]
                                      (handler (assoc req :hifi.assets/resolver (:hifi.assets/resolver config)))))})
    :donut.system/config {:hifi.assets/resolver [:donut.system/ref [:hifi/assets :hifi.assets/resolver]]}}))
