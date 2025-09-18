;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

(ns hifi.assets
  "Asset pipeline for HIFI framework - provides digest-based asset management,
   HTML helpers, and development/production asset serving."
  (:require
   [clojure.java.io :as io]
   [hifi.assets.config :as config]
   [hifi.assets.manifest :as manifest]))

(defn create-asset-context
  "Creates an asset context map containing configuration, manifest data,
   and environment settings. This context is passed to all asset functions.

   Options:
   - :config - Assets configuration map
   - :dev-mode? - Whether to run in development mode (default: false) "
  ([opts]
   (let [config (config/load-config (:config opts))
         dev-mode? (boolean (:dev-mode? opts))
         manifest-path (:hifi.assets/manifest-path config)
         manifest (when-not dev-mode?
                    (manifest/load-manifest manifest-path))]
     {:config config
      :dev-mode? dev-mode?
      :manifest manifest
      :manifest-path manifest-path}))
  ([]
   (create-asset-context {})))

(defn asset-path
  "Returns the path to an asset, applying digest/cache-busting in production.
   In development mode, returns the logical path unchanged.
   In production, looks up the digested path from the manifest."
  [asset-ctx logical-path]
  (if (:dev-mode? asset-ctx)
    logical-path
    (or (get-in (:manifest asset-ctx) [logical-path :digest-path])
        logical-path)))

(defn asset-integrity
  "Returns the SRI (Subresource Integrity) hash for an asset.
   Returns nil in development mode or if no integrity hash exists."
  [asset-ctx logical-path]
  (when-not (:dev-mode? asset-ctx)
    (get-in (:manifest asset-ctx) [logical-path :integrity])))

(defn asset-exists?
  "Checks if an asset exists in the configured asset paths or manifest."
  [asset-ctx logical-path]
  (if (:dev-mode? asset-ctx)
    ;; In dev mode, check if file exists in any configured path
    (let [paths (get-in asset-ctx [:config :hifi.assets/paths])]
      (some #(let [abs-path (str % "/" logical-path)]
               (.exists (io/file abs-path)))
            paths))
    ;; In production, check manifest
    (contains? (:manifest asset-ctx) logical-path)))

(defn stylesheet-link-tag
  "Generates a hiccup :link element for a CSS stylesheet.

   Options:
   - :integrity - Include SRI integrity attribute (default: false)
   - :media - CSS media attribute (default: 'all')
   - :rel - Link relationship (default: 'stylesheet')
   - Any other attributes are passed through to the tag"
  ([asset-ctx logical-path]
   (stylesheet-link-tag asset-ctx logical-path {}))
  ([asset-ctx logical-path opts]
   (let [href (asset-path asset-ctx logical-path)
         base-attrs {:rel (or (:rel opts) "stylesheet")
                     :type "text/css"
                     :href href
                     :media (or (:media opts) "all")}
         integrity-hash (when (:integrity opts)
                          (asset-integrity asset-ctx logical-path))
         attrs (cond-> (merge base-attrs (dissoc opts :integrity))
                 integrity-hash (assoc :integrity integrity-hash))]
     [:link attrs])))

(defn script-tag
  "Generates a hiccup :script element for JavaScript.

   Options:
   - :integrity - Include SRI integrity attribute (default: false)
   - :async - Add async attribute (default: false)
   - :defer - Add defer attribute (default: false)
   - :type - Script type (default: 'text/javascript')
   - Any other attributes are passed through to the tag"
  ([asset-ctx logical-path]
   (script-tag asset-ctx logical-path {}))
  ([asset-ctx logical-path opts]
   (let [src (asset-path asset-ctx logical-path)
         base-attrs {:src src
                     :type (or (:type opts) "text/javascript")}
         integrity-hash (when (:integrity opts)
                          (asset-integrity asset-ctx logical-path))
         attrs (cond-> (merge base-attrs (dissoc opts :integrity :async :defer))
                 integrity-hash (assoc :integrity integrity-hash)
                 (:async opts) (assoc :async true)
                 (:defer opts) (assoc :defer true))]
     [:script attrs])))

(defn image-tag
  "Generates a hiccup :img element for images.

   Options:
   - :alt - Alt text for the image
   - :width - Image width
   - :height - Image height
   - :class - CSS class(es)
   - Any other attributes are passed through to the tag"
  ([asset-ctx logical-path]
   (image-tag asset-ctx logical-path {}))
  ([asset-ctx logical-path opts]
   (let [src (asset-path asset-ctx logical-path)
         attrs (merge {:src src} opts)]
     [:img attrs])))

(defn preload-link-tag
  "Generates a hiccup :link element for asset preloading.

   Options:
   - :as - Resource type (e.g., 'script', 'style', 'image')
   - :type - MIME type
   - :integrity - Include SRI integrity attribute (default: false)
   - Any other attributes are passed through to the tag"
  ([asset-ctx logical-path opts]
   (let [href (asset-path asset-ctx logical-path)
         base-attrs {:rel "preload"
                     :href href}
         integrity-hash (when (:integrity opts)
                          (asset-integrity asset-ctx logical-path))
         attrs (cond-> (merge base-attrs (dissoc opts :integrity))
                 integrity-hash (assoc :integrity integrity-hash))]
     [:link attrs])))

(defn javascript-include-tags
  "Generates multiple hiccup :script elements for a collection of JavaScript paths.
   Returns a fragment (list) of script elements.
   Passes opts to each individual script-tag call."
  ([asset-ctx paths]
   (javascript-include-tags asset-ctx paths {}))
  ([asset-ctx paths opts]
   (map #(script-tag asset-ctx % opts) paths)))

(defn stylesheet-link-tags
  "Generates multiple hiccup :link elements for a collection of CSS paths.
   Returns a fragment (list) of link elements.
   Passes opts to each individual stylesheet-link-tag call."
  ([asset-ctx paths]
   (stylesheet-link-tags asset-ctx paths {}))
  ([asset-ctx paths opts]
   (map #(stylesheet-link-tag asset-ctx % opts) paths)))
