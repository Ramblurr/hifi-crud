;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.config
  (:refer-clojure :exclude [mask])
  (:require
   [time-literals.data-readers]
   [time-literals.read-write]
   [exoscale.cloak :as cloak]
   [aero.core :as aero]
   [clojure.java.io :as io]))

(time-literals.read-write/print-time-literals-clj!)

(def ^:dynamic *env*
  nil)

(defn set-env!
  "Sets the current profile for the REPL. This is useful for development
  when you want to change the profile without restarting the REPL."
  [profile]
  (alter-var-root #'*env* (constantly profile)))

(defn current-profile
  "Returns the current profile value or nil if it does not exist

  The profile can be set via (in priority order):
  - `HIFI_PROFILE` environment variablex
  - `hifi.profile` JVM property
  - [[*env*]] - a dynamic var for use during REPL driven development, so you don't have to restart your REPL to change your profile

  Possible options are:
    - `nil` or `:prod` for production
    - `:dev` for development"
  []
  (or
   *env*
   (keyword (System/getenv "HIFI_PROFILE"))
   (keyword (System/getProperty "hifi.profile"))))

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
  "Returns true if x is a value wrapped by the Secret type, false otherwise"
  [x]
  (cloak/secret? x))

(defmethod aero/reader 'hifi/secret
  ;; Implementation for #hifi/secret tag literal in an Aero config edn
  ;; It masks #hifi/secret tagged values so they cannot be printed.
  [_ _ value]
  (mask value))

(defn -read-config
  "Reads the `filename` file from the classpath and returns a map.

   Options are passed to the underlying Aero reader."
  [filename options]
  ;;; This exists because we want to be able to provide nicer error messages.
  (if-let [f filename]
    (aero/read-config f  options)
    (throw (ex-info (str "Config filename '" filename "' does not exist or could not be read by hifi.config/read-config") {:hifi/error :hifi.config/invalid-filename :filename filename :options options}))))

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
      :or   {filename "env.edn"
             opts     {}}}]

  (let [profile   (current-profile)
        aero-opts (merge (when profile {:profile profile}) opts)]
    (-> (-read-config (io/resource filename) aero-opts)
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

(defn prod? []
  (let [p (current-profile)]
    (or (nil? p)
        (= :prod p))))

(defn dev? []
  (= :dev (current-profile)))
