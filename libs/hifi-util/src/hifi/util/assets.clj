;; Copyright © 2025 Anders Murphy
;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; Portions of this file are based on hyperlith code from @Anders
;; https://github.com/andersmurphy/hyperlith/
;; SPDX-License-Identifier: MIT
(ns hifi.util.assets
  (:import [java.io InputStream])
  (:require
   [clojure.java.io :as io]
   [hifi.util.crypto :as crypto]))

(defn resource [p]
  (if-let [res (io/resource p)]
    res
    (throw (ex-info "Resource does not exist" {:resource-path p}))))

(defn resource->bytes [resource]
  (-> resource io/input-stream InputStream/.readAllBytes))

(defmacro load-resource
  "Fails at compile time if resource doesn't exists."
  [path]
  (let [res (io/resource path)]
    (assert res (str path " not found."))
    `(resource->bytes (io/resource ~path))))

(defn static-asset
  [reload? {:keys [resource-path route-path content-type compress-fn encoding integrity]}]
  (let [route-path (or route-path
                       (when resource-path
                         (str "/"
                              (.getName (io/as-file (io/resource resource-path))))))
        _ (assert route-path)
        compute (fn []
                  (let [resp (cond-> {:status 200
                                      :headers {"Cache-Control" "max-age=31536000, immutable"
                                                "Content-Type" content-type}
                                      :body (resource->bytes (resource resource-path))}
                               compress-fn (update :body compress-fn)
                               compress-fn (assoc-in [:headers "Content-Encoding"] encoding))
                        sri-hash (crypto/sri-sha384-resource resource-path)
                        cache-buster (subs sri-hash (- 71 8))]
                    {:handler (fn [_]
                                resp)
                     ;; :integrity (if (some? integrity) integrity sri-hash)
                     :href (str route-path "?v=" cache-buster)
                     :path route-path}))]

    (if reload?
      ;; In dev mode, re-evaluate on every deref
      (reify clojure.lang.IDeref
        (deref [_] (compute)))
      ;; In prod, the delay will cache the value
      (delay (compute)))))

(defn asset->route
  "Returns a reitit route vector for the !asset"
  [!asset]
  (let [asset @!asset]
    [(asset :path) {:handler (asset :handler)}]))
