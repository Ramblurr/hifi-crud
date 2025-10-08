(ns hifi.cli.cmd.shared
  (:require
   [clojure.string :as str]
   [babashka.fs :as fs]
   [babashka.cli :as cli]
   [hifi.config :as config]))

(def bin-name "hifi")

(def shared-specs {:help        {:desc  "Show this help message"
                                 :alias :h}
                   :config-file {:desc    "The hifi config file to load"
                                 :alias   :c
                                 :default "config/hifi.edn"}})

(defn with-shared-specs
  ([ks] (with-shared-specs ks {}))
  ([ks spec]
   (reduce (fn [spec k]
             (assoc spec k (get shared-specs k))) spec ks)))

;; ANSI color codes
(def ^:private colors
  {:red    "\033[0;31m"
   :green  "\033[0;32m"
   :yellow "\033[1;33m"
   :purple "\033[0;35m"
   :reset  "\033[0m"})

(defn colorize
  "Apply ANSI color to text"
  [color text]
  (str (get colors color "") text (:reset colors)))

(defn info
  "Print informational message to stdout"
  [& args]
  (apply println (str (colorize :green "Info:") " ") args))

(defn error
  "Print error message to stderr"
  [& args]
  (binding [*out* *err*]
    (apply println (str (colorize :red "Error:") " ") args)))

(defn warn
  "Print warning message to stderr"
  [& args]
  (binding [*out* *err*]
    (apply println (str (colorize :yellow "Warning:") " ") args)))

(defn exit-msg [& msg]
  (apply error msg)
  (System/exit 1))

(defn print-examples [examples]
  (when (seq examples)
    (println "EXAMPLES:")))

(defn load-config [{:keys [config-file profile] :as _opts}]
  (if (fs/exists? config-file)
    (try
      (config/read-config config-file {:profile profile})
      (catch Exception e
        (println (ex-message e))
        ;; (println (ex-data e))
        (exit-msg (format "the config for your app failed to parse, please fix the issue in '%s'. " config-file))))
    (exit-msg (format "the config for your app is missing, the file '%s' does not exist. " config-file))))

(defn print-args [args]
  (when (seq args)
    (println "Arguments:")
    (println (cli/format-table {:rows (map (fn [{:keys [desc ref]}]
                                             [ref desc]) args)
                                :indent 2}))))
(defn args-summary [args]
  (->> args
       (map :ref)
       (str/join)))

(defn help-printer [spec]
  (fn [args]
    (let [subcommand (first (:dispatch args))]
      (println)
      (when-let [d (:description spec)]
        (println d))
      (println)
      (println (format "Usage: \n  %s %s [flags] %s" bin-name subcommand (args-summary (:args spec))))
      (println)
      (when-let [doc (:doc spec)]
        (println doc)
        (println))
      (print-args (:args spec))
      (println)
      (println "Flags:")
      (println (cli/format-opts spec))
      (println)
      (print-examples (:examples spec)))))

(defn with-help [handler spec]
  (assoc spec :fn
         (fn [{:keys [opts] :as i}]
           (if (:help opts)
             ((help-printer spec) i)
             (handler i)))))

(defn write-file
  ([f contents]
   (write-file f contents nil))
  ([f contents {:keys [overwrite?]}]
   (if (or (not (fs/exists? f)) overwrite?)
     (do (spit f contents) (info "Created " (str f)) nil)
     {:error (str f " already exists")})))
