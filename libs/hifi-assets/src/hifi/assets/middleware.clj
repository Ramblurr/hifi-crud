(ns hifi.assets.middleware
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [hifi.assets :as assets]
   [hifi.assets.process :as process])
  (:import
   (java.net URLDecoder)))

(def ^:private not-found-response
  {:status  404
   :headers {"Content-Type"   "text/plain"
             "Content-Length" "9"}
   :body    "Not found"})

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

(defn- build-headers [entry logical-path]
  (let [size         (:size entry)
        etag         (when-let [digest (extract-digest (:digest-path entry))]
                       (str "\"" digest "\""))
        content-type (process/infer-mime process/default-ext->mime logical-path)
        headers      {"Content-Type"    content-type
                      "Cache-Control"  "public, max-age=31536000, immutable"
                      "Vary"           "Accept-Encoding"}
        headers      (if size
                       (assoc headers "Content-Length" (str size))
                       headers)
        headers      (if etag
                       (assoc headers "ETag" etag)
                       headers)
        headers      (if-let [last-modified (:last-modified entry)]
                       (assoc headers "Last-Modified" last-modified)
                       headers)]
    headers))

(defn- asset-response [asset-ctx prefix request]
  (let [{:keys [manifest]} asset-ctx
        uri                (:uri request)
        method             (:request-method request)]
    (when (and uri (#{:get :head} method) (str/starts-with? uri prefix))
      (let [digest-path (request-digest-path prefix uri)
            entry       (find-manifest-entry manifest digest-path)]
        (if (and entry (= digest-path (:digest-path entry)))
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
              {:status 200
               :headers headers
               :body    nil}

              :else
              (if-let [stream (assets/asset-read asset-ctx logical-path)]
                {:status 200
                 :headers headers
                 :body    stream}
                not-found-response)))
          not-found-response)))))

(defn assets-middleware
  ([] (assets-middleware {}))
  ([opts]
   (let [asset-ctx (or (:asset-ctx opts)
                       (assets/create-asset-context opts))
         prefix    (or (get-in asset-ctx [:config :hifi.assets/prefix]) "/assets")]
     {:name ::assets
      :wrap (fn assets-wrap [handler]
              (fn
                ([request]
                 (or (asset-response asset-ctx prefix request)
                     (handler request)))
                ([request respond raise]
                 (if-let [resp (asset-response asset-ctx prefix request)]
                   (respond resp)
                   (handler request respond raise)))))})))
