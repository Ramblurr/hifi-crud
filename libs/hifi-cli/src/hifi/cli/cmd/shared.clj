(ns hifi.cli.cmd.shared
  (:require
   [hifi.cli.terminal :as term]
   [clojure.string :as str]
   [babashka.fs :as fs]
   [hifi.config :as config]))

(def bin-name "hifi")

(def shared-specs {:help        {:desc  "Show this help message"
                                 :alias :h}
                   :verbose     {:verbose "Verbose output"
                                 :coerce  :boolean}
                   :debug       {:verbose "Print additional logs and traces"
                                 :coerce  :boolean}
                   :config-file {:desc    "The hifi config file to load"
                                 :alias   :c
                                 :default "config/hifi.edn"}})

(defn with-shared-specs
  ([ks] (with-shared-specs ks {}))
  ([ks spec]
   (reduce (fn [spec k]
             (assoc spec k (get shared-specs k))) spec ks)))

(defn ->args
  "Build a clean argv from a soup of values and nested seqs.
   - Flattens one level (so (when … [k v]) works)
   - Removes nils
   - If an element looks like an option key (keyword or string starting with \":\")
     and its *next* value is nil/blank, drop the whole pair."
  [& xs]
  (let [flat (mapcat #(if (sequential? %) % [%]) xs)
        opt-key? (fn [x]
                   (or (keyword? x)
                       (and (string? x) (str/starts-with? x ":"))))]
    (loop [acc [] [x & more] flat]
      (cond
        (nil? x) acc

        (opt-key? x)
        (let [v (first more)
              more' (rest more)]
          (if (or (nil? v)
                  (and (string? v) (str/blank? v)))
            (recur acc more')              ; drop the whole pair
            (recur (conj acc x v) more')))

        :else
        (recur (conj acc x) more)))))

(defn load-config [{:keys [config-file profile] :as _opts}]
  (if (fs/exists? config-file)
    (try
      (config/read-config config-file {:profile profile})
      (catch Exception e
        (println (ex-message e))
        ;; (println (ex-data e))
        (throw (term/error (format "the config for your app failed to parse, please fix the issue in '%s'. " config-file)))))
    (throw (term/error (format "the config for your app is missing, the file '%s' does not exist. " config-file)))))
