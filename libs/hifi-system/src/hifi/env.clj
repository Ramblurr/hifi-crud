;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.env
  (:refer-clojure :exclude [mask])
  (:require
   [exoscale.cloak :as cloak]
   [aero.core :as aero]
   [clojure.java.io :as io]))

(defn get-env-profile
  "Returns the value of HIFI_PROFILE or nil if it does not exist"
  []
  (System/getenv "HIFI_PROFILE"))

(defn mask
  "Mask a value behind the `Secret` type, hiding its real value when printing"
  [x]
  (cloak/mask x))

(defn unmask
  "Reveals all potential secrets from `x`, returning the value with secrets
  unmasked, works on any walkable type"
  [x]
  (cloak/unmask x))

(defn secret?
  "Returns true if x is a value wrapped by the Secret type, false otherwise"
  [x]
  (cloak/secret? x))

(defmethod aero/reader 'secret
  ;; Implementation for #secret tag literal in an Aero config edn
  ;; It maskes #secret tagged values so they cannot be printed.
  [_ _ value]
  (mask value))

(defn read-config
  "Reads the `filename` file from the classpath and returns a map.

   Options are passed to the underlying Aero reader."
  [filename options]
  ;;; Why does this fn exist? Because we want to be able to provide nicer error messages.
  (if-let [f (io/resource filename)]
    (aero/read-config f  options)
    (throw (ex-info (str "Config filename '" filename "' does not exist or could not be read by hifi.env/read-config") {:hifi/error :hifi.env/invalid-filename :filename filename :options options}))))

(defn read-env
  "Reads `:env-filename` (default: env.edn`) and returns the env config.

   Accepts key-value pairs, all are optional.
   - `:env-filename`, a string containing a filename, the env EDN file to load
   - `:profile`, a keyword, can be passed to load a specific profile (e.g., `:dev`, `:test`, etc), by default this is nil, which is probably the production profile
   - `:opts`, a map, are  extra Aero options

   This is a more opinionated version of `hifi.env/read-config` with a naming convention.

   Example:

   ```clojure
   (hifi.env/read-env)                 ;; reads env.edn with the default profile
   (hifi.env/read-env :profile :dev)   ;; reads env.edn with the :dev profile
   ```

   Options are passed to the underlying Aero reader."
  [& {:keys [profile env-filename opts]
      :or   {env-filename "env.edn"
             profile      (get-env-profile)
             opts         {}}}]

  (let [aero-opts (merge (when profile {:profile profile}) opts)]
    (-> (read-config env-filename aero-opts)
        (assoc :profile profile))))
