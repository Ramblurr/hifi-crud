;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.config.impl
  (:refer-clojure :exclude [mask])
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.walk :as walk]
   [exoscale.cloak :as cloak]
   [hifi.core :as h]
   [ol.sops :as sops]
   [time-literals.data-readers]
   [time-literals.read-write])
  (:import
   [java.io StringReader]))

(time-literals.read-write/print-time-literals-clj!)

(defn mask
  [x]
  (cloak/mask x))

(defn unmask
  [x]
  (cloak/unmask x))

(defn unmask!
  [x]
  (let [v (cloak/unmask x)]
    (if (nil? v)
      (throw (ex-info "Unmasked secret is nil" {:hifi/error                    :hifi.config/unmask-nil
                                                :hifi.anomalies/category :hifi.anomalies/incorrect}))
      v)))

(defn secret?
  [x]
  (cloak/secret? x))

(defn secret-present?
  [x]
  (and
   (secret? x)
   (some? (unmask x))))

(defn mask-deep
  [x]
  (walk/postwalk #(cond
                    (secret? %) %
                    (map? %)    (into {} (map (fn [[k v]] [k (if (or (map? v) (vector? v)) v (mask v))]) %))
                    (vector? %) %
                    :else       %)
                 x))

(defmethod aero/reader 'hifi/secret
  ;; Implementation for #hifi/secret tag literal in an Aero config edn
  ;; It masks #hifi/secret tagged values so they cannot be printed.
  [_ _ value]
  (mask value))

(defn relative-resolver [source include]
  (let [fl (if (.isAbsolute (io/file include))
             (io/file include)
             (when-let [source-file
                        (try (io/file source)
                             ;; Handle the case where the source isn't file compatible:
                             (catch java.lang.IllegalArgumentException _ nil))]
               (io/file (.getParent ^java.io.File source-file) include)))]
    (when (and fl (.exists fl))
      fl)))

(defmethod aero/reader 'hifi/sops
  ;; Implementation for #hifi/sops tag literal in an Aero config edn
  ;; It assumes the value is a path to a file then tries to decrypt it with (sops/decrypt-file-to-str "dev/test.sops.yml")
  [{:keys [source :hifi/sops] :as opts} _tag value]
  (if-let [resolved-file (relative-resolver source value)]
    (-> resolved-file
        (sops/decrypt-file-to-str (merge {:input-type "binary"} sops))
        (StringReader.)
        (aero/read-config opts)
        (mask-deep))
    (throw (ex-info (format "Referenced file does not exist '%s'" value) {:include value}))))

(defn read-config
  [source opts]
  (let [opts (merge {:profile (h/current-profile)} opts)]
    (when-not source (throw (ex-info "Filename passed to hifi.config/read-config was nil" {:hifi/error :hifi.config/invalid-file :filename nil})))
    (try
      (-> source
          (aero/read-config opts)
          (assoc :profile (:profile opts)))

      (catch java.io.FileNotFoundException _
        (throw (ex-info (str "Config source '" source "' does not exist or could not be read by hifi.config/read-config") {:hifi/error :hifi.config/invalid-file :source source :opts opts}))))))
