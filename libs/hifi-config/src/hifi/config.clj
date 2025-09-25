;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.config
  (:refer-clojure :exclude [mask])
  (:require
   [hifi.core :as h]
   [time-literals.data-readers]
   [time-literals.read-write]
   [exoscale.cloak :as cloak]
   [aero.core :as aero]
   [clojure.java.io :as io]))

(time-literals.read-write/print-time-literals-clj!)

(defn mask
  "Mask a value behind the `Secret` type, hiding its real value when printing"
  [x]
  (cloak/mask x))

(defn unmask
  "Reveals all potential secrets from `x`, returning the value with secrets
  unmasked, works on any walkable type"
  [x]
  (cloak/unmask x))

(defn unmask!
  "Like [[unmask]] but throws if the unmasked value is nil"
  [x]
  (let [v (cloak/unmask x)]
    (if (nil? v)
      (throw (ex-info "Unmasked secret is nil" {:hifi/error                    :hifi.config/unmask-nil
                                                :hifi.anomalies.iface/category :hifi.anomalies.iface/incorrect}))
      v)))

(defn secret?
  "Returns true if x is a value wrapped by the Secret type"
  [x]
  (cloak/secret? x))

(defn secret-present?
  "Returns true if x is a value wrapped by the Secret type and the wrapped value is not nil, false otherwise"
  [x]
  (and
   (secret? x)
   (some? (unmask x))))

(defmethod aero/reader 'hifi/secret
  ;; Implementation for #hifi/secret tag literal in an Aero config edn
  ;; It masks #hifi/secret tagged values so they cannot be printed.
  [_ _ value]
  (mask value))

(defn -read-config
  "Reads the `filename` file from the classpath and returns a map.

   Options are passed to the underlying Aero reader."
  [filename options]
  (try
    (if filename
      (aero/read-config filename options)
      (throw (ex-info "Filename passed to hifi.config/read-config was nil" {:hifi/error :hifi.config/invalid-file :filename nil :options options})))
    (catch java.io.FileNotFoundException _
      (throw (ex-info (str "Config filename '" filename "' does not exist or could not be read by hifi.config/read-config") {:hifi/error :hifi.config/invalid-file :filename filename :options options})))))

(defn read-config
  "Reads `:filename` (default: env.edn`) and returns the env config.

   Accepts key-value pairs, all are optional.
   - `:filename`, a string containing a filename, the env EDN file to load
   - `:opts`, a map, are  extra Aero options

   This is a more opinionated version of `hifi.config/read-config` with a naming convention.

   The active profile is controlled by the HIFI_PROFILE environment variable.

   Example:

   ```clojure
   (hifi.config/read-config)                 ;; reads env.edn with the default profile
   ```

   Options are passed to the underlying Aero reader."
  [& {:keys [filename opts]
      :or   {filename (io/resource "env.edn")
             opts     {}}}]

  (let [profile   (h/current-profile)
        aero-opts (merge (when profile {:profile profile}) opts)]
    (-> (-read-config filename aero-opts)
        (assoc :profile profile))))

(comment
  (def env-data
    (dissoc (read-config) :hifi/components))

  (defmacro env
    "Read env from .env.edn. If env is missing fails at compile time."
    [k]
    (if (k env-data)
      ;; We could just inline the value, but that makes it trickier
      ;; to patch env values on a running server from the REPL.
      `(env-data ~k)
      (throw (ex-info (str "Missing env in .env.edn: " k)
                      {:hifi/error :hifi.config/missing-env
                       :env-key    k})))))
