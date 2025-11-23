(ns hifi.cli.extension
  "Extensions on top of babashka.cli for building CLIs with a nested command tree,
   progressive flag parsing, middleware wrapping, and automatic and contextual help.

   Key ideas:
   - Represent commands as a nested map (a command tree), where each node may define:
     :fn, :spec, :args->opts, :help and string keys for subcommands.
   - Parse shared flags at each level as you descend, merging specs/flags along the way.
   - Return a context map describing the dispatch decision and call the selected :fn.
   - Compose behavior around the selected :fn via simple middleware wrappers.
   - Provide contextual help and 'did you mean?' suggestions.

   Public API:
   - dispatch               => run CLI using the tree, args and flagal {:middleware [...], :error-fn ...}
   - help                   => print help for the current or given subcommand path
   - error-fn               => drop-in :error-fn for babashka.cli that collects errors
   - reset-errors!, errors  => manage collected parse errors

   Example usage:

   (def cmd-tree
     {\"tool\"
      {:help \"Tooling commands\"
       \"ping\" {:fn (fn [{:keys [opts]}] (println \"pong\")) :help \"Ping the tool\"}
       \"echo\" {:fn (fn [{:keys [args]}] (println (str/join \" \" args)))
                :spec {:upper {:alias :u :coerce :boolean :desc \"Uppercase output\"}}
                :help \"Echo args\"}}})

   (def middleware
     [wrap-with-help
      wrap-with-quiet
      wrap-with-output-format
      wrap-with-error-reporting
      wrap-with-exit-code
      wrap-with-debug])

   (dispatch cmd-tree *command-line-args* {:middleware middleware
                                           :error-fn error-fn})"
  (:require [babashka.cli :as cli]
            [hifi.cli.terminal :as term]
            [clojure.string :as str])
  (:import [java.io PrintWriter]))

;; -----------------------------------------------------------------------------
;; Utilities
;; -----------------------------------------------------------------------------

(defn keyword-map
  "Keep only keyword keys from m. Useful to extract parse-opts relevant keys
   like :spec, :alias, :coerce, :args->opts from a node."
  [m]
  (select-keys m (filter keyword? (keys m))))

(defn ->flag [k]
  (str "--" (name k)))

(defonce ^:private !errors (atom []))

(defn errors
  "Return the accumulated parse error messages."
  []
  @!errors)

(defn reset-errors!
  "Reset accumulated parse errors."
  []
  (reset! !errors []))

(defn error-fn
  "Default :error-fn suitable for passing into babashka.cli parse functions.
   Collects friendly messages into an internal atom for later reporting."
  [{:as m :keys [cause option]}]
  (swap! !errors conj
         (case cause
           :require  (format "Missing option: %s" (->flag option))
           :validate (format "Invalid value for flag %s" (->flag option))
           :coerce   (format "Invalid value for ag %s" (->flag option))
           :restrict (format "Invalid flag %s" (->flag option))
           (or (:msg m) "Error"))))

(defn deep-merge
  "Deeply merges maps a and b. Values from b override a.
   Only recurses into submaps."
  [a b]
  (reduce (fn [acc k]
            (update acc k (fn [v]
                            (if (map? v)
                              (deep-merge v (b k))
                              (b k)))))
          a (keys b)))

(defn has-parse-opts?
  "Heuristic: does a node m contain parse-related configuration?"
  [m]
  (some #{:spec :coerce :require :restrict :validate :args->opts :exec-args} (keys m)))

(defn parse-opts-keys
  "Extract only the parse-opts-related keys from a node."
  [m]
  (select-keys m [:spec :coerce :require :restrict :validate :args->opts :exec-args]))

(defn collect-all-specs
  "Recursively collect all :spec maps from cmd-info and its subcommands."
  [cmd-info]
  (let [current-spec (:spec (keyword-map cmd-info))
        subcommands (filter string? (keys cmd-info))
        sub-specs (map #(collect-all-specs (get cmd-info %)) subcommands)
        all-specs (cons current-spec sub-specs)]
    (reduce deep-merge {} (filter some? all-specs))))

(defn is-option?
  "Is s an option-like token (starts with '-' or '--')?"
  [s]
  (some-> s (str/starts-with? "-")))

(defn indent
  "Indent a multiline string by n spaces."
  [n s]
  (->> (str/split-lines (or s ""))
       (map (fn [line] (str (apply str (repeat n " ")) line)))
       (str/join "\n")))

;; -----------------------------------------------------------------------------
;; Did you mean?
;; -----------------------------------------------------------------------------

(defn ^:private levenshtein
  [^String s ^String t]
  (let [m (inc (count s))
        n (inc (count t))
        dp (make-array Long/TYPE m n)]
    (dotimes [i m] (aset-long dp i 0 i))
    (dotimes [j n] (aset-long dp 0 j j))
    (doseq [i (range 1 m)]
      (doseq [j (range 1 n)]
        (let [cost (if (= (.charAt s (dec i)) (.charAt t (dec j))) 0 1)
              del  (inc (aget dp (dec i) j))
              ins  (inc (aget dp i (dec j)))
              sub  (+ (aget dp (dec i) (dec j)) cost)]
          (aset-long dp i j (min del ins sub)))))
    (aget dp (dec m) (dec n))))

(defn candidates
  "Pick top-N closest strings to wrong-input from options (by Levenshtein).
   Returns a vector of strings."
  [wrong-input options n]
  (->> options
       (map (fn [o] [o (levenshtein wrong-input o)]))
       (sort-by second)
       (map first)
       (take n)
       vec))

;; -----------------------------------------------------------------------------
;; Help rendering
;; -----------------------------------------------------------------------------

(defn signature
  "Return a signature string like \"a b <arg1> <arg2>\" for the command path."
  [cmd-tree cmds]
  (when (seq cmds)
    (when-let [{:keys [args->opts]} (get-in cmd-tree cmds)]
      (str/join " " (concat cmds ["[flags]"] (map #(str "<" (name %) ">") args->opts))))))

(defn description
  "Return the :desc string for a command path."
  [cmd-tree cmds]
  (:desc (get-in cmd-tree cmds)))

(defn doc
  "Return the :doc string for a command path."
  [cmd-tree cmds]
  (:doc (get-in cmd-tree cmds)))

(defn examples
  "Return the :examples string for a command path."
  [cmd-tree cmds]
  (:examples (get-in cmd-tree cmds)))

(defn flags-text
  "Format options for a command path using babashka.cli/format-opts.
   Merges global flags with command-specific flags."
  [cmd-tree cmds]
  (let [;; Collect all specs from root to current command
        specs (loop [path []
                     remaining-cmds (vec cmds)
                     acc []]
                (let [node (get-in cmd-tree path)
                      spec (when node (:spec node))
                      acc' (if spec (conj acc spec) acc)]
                  (if (empty? remaining-cmds)
                    acc'
                    (recur (conj path (first remaining-cmds))
                           (rest remaining-cmds)
                           acc'))))
        ;; Merge all specs together, last one wins for conflicts
        merged-spec (reduce deep-merge {} (filter some? specs))
        s (cli/format-opts {:spec merged-spec :indent 0})]
    (when-not (str/blank? s) s)))

(defn subcommand-help-text
  "Return a formatted table of subcommands and their help texts under a path."
  [cmd-tree cmds]
  (let [subcommands (sort (filter string? (keys (get-in cmd-tree cmds))))]
    (when (seq subcommands)
      (cli/format-table
       {:rows (mapv (fn [c]
                      (let [sub (conj (vec cmds) c)]
                        [(str/join " " sub) (str "\t" (description cmd-tree sub))]))
                    subcommands)
        :indent 0}))))

(defn print-command-usage [cmd-tree command bin-name]
  (println)
  (println (description cmd-tree command))
  (println)
  (println "Usage: \n " bin-name (signature cmd-tree command))
  (when-let [doc (doc cmd-tree command)]
    (println)
    (println doc))
  (println))

(defn print-command-flags [cmd-tree command]
  (when-let [s (flags-text cmd-tree command)]
    (println "Flags:")
    (println (indent 2 s))
    (println)))

(defn print-available-commands [cmd-tree command]
  (when-let [s (subcommand-help-text cmd-tree command)]
    (println "Available commands:")
    (println (indent 2 s))))

(defn  print-examples [cmd-tree command]
  (when (seq (examples cmd-tree command))
    (println "Examples:")
    (println)
    (println (examples cmd-tree command))))

(defn print-command-help [cmd-tree args bin-name]
  (print-command-usage cmd-tree args bin-name)
  (print-command-flags cmd-tree args)
  (print-available-commands cmd-tree args)
  (print-examples cmd-tree args)
  (let [subcommands (filter string? (keys (get-in cmd-tree args)))]
    (when (seq subcommands)
      (let [cmd-path (if (empty? args) "" (str (str/join " " args) " "))]
        (println (format "Use \"%s %s[command] --help\" for more information about a command." bin-name cmd-path))))))

(defn help
  "Print contextual help.
   Accepts either:
   - {:cmd-tree tree :args [\"sub\" ...] :bin-name \"prog\"} to show help for a subcommand path
   - {:cmd-tree tree :bin-name \"prog\"} or {:cmd-tree tree :args nil :bin-name \"prog\"} to show top-level commands"
  [{:keys [cmd-tree args bin-name]}]
  (let [args (vec args)
        bin-name (or bin-name "my-cli")]
    (cond
      (empty? args)
      (do
        (println)
        (print-available-commands cmd-tree []))

      (get-in cmd-tree args)
      (print-command-help cmd-tree args bin-name)

      :else
      (if-let [valid-prefix (loop [path (vec args)]
                              (cond
                                (empty? path) nil
                                (get-in cmd-tree path) path
                                :else (recur (pop path))))]
        (let [last-invalid-cmd (get args (count valid-prefix))]
          (println)
          (term/msg [:bold.error "Error: "] (format "unknown command '%s' for '%s'" last-invalid-cmd (str/join " " valid-prefix)))
          (println)
          (print-command-help cmd-tree valid-prefix bin-name))
        (do
          (println)
          (term/msg [:bold.error "Error: "] (format "unknown command %s" (str/join " " args)))
          (print-available-commands cmd-tree []))))))

(comment
  (def _tree {"new"
              {"sub" {:desc "Wow much tool"
                      :spec {:wow {}}}
               :spec
               {:overwrite
                {:coerce :boolean, :desc "Whether to overwrite an existing directory", :default false},
                :template-coord {:default "hifi/cli/template"},
                :target-dir
                {:desc "Defines the directory which the new project is created in. \nBy default it will be the name part of your project-name\nexample: com.example/my-app -> \"my-app/\" "},
                :help {:desc "Show this help message", :alias :h}},
               :args
               [{:desc
                 "The name of the project, must be a qualified project name like com.example/my-app",
                 :ref "<project-name>"}],
               :args->opts [:project-name],
               :examples [],
               :desc "Create a new hifi project in the current directory",
               :doc "Extra docs",
               :cmds ["new"]}

              "tool"
              {:desc "Tooling commands"
               "ping" {:fn (fn [{:keys [opts]}] (println "pong")) :desc "Ping the tool"}
               "echo" {:fn (fn [{:keys [args]}] (println (str/join " " args)))
                       :spec {:upper {:alias :u :coerce :boolean :desc "Uppercase output"}}
                       :desc "Echo args"}}})

  (help {:cmd-tree _tree :args ["new" "wtf"]})
  ;; rcf
  )

;; -----------------------------------------------------------------------------
;; Dispatch: nested command tree + progressive parse-args
;; -----------------------------------------------------------------------------

(defn dispatch-tree
  "Walk the nested command tree, progressively parsing options at each level,
   and return a context map.

   Returns one of:
   - {:cmd-info node, :dispatch [..], :opts {...}, :args [...], :cmd-tree tree, :bin-name \"...\"}
   - {:error :no-match, :dispatch [..], :wrong-input token, :available-commands [...], :cmd-tree tree, :bin-name \"...\"}
   - {:error :input-exhausted, :dispatch [..], :available-commands [...], :cmd-tree tree, :bin-name \"...\"}"
  [tree args opts]
  (let [bin-name (or (:bin-name opts) "hifi")]
    (loop [cmds     []
           all-opts {}
           args     (seq args)
           cmd-info tree
           accumulated-spec {}]
      (let [m                   (keyword-map cmd-info)
            parse-keys          (parse-opts-keys m)
            current-spec        (:spec parse-keys)
            merged-spec         (if current-spec
                                  (deep-merge accumulated-spec current-spec)
                                  accumulated-spec)
            all-descendant-specs (collect-all-specs cmd-info)
            spec-for-restrict   (deep-merge merged-spec all-descendant-specs)
            args->opts          (:args->opts parse-keys)
            spec-with-args      (if args->opts
                                  (reduce (fn [spec arg-key]
                                            (assoc spec arg-key {}))
                                          spec-for-restrict
                                          args->opts)
                                  spec-for-restrict)
            should-parse-args?  (or (has-parse-opts? m)
                                    (is-option? (first args)))
            allowed-keys        (set (keys spec-with-args))
            exec-defaults       (select-keys all-opts allowed-keys)
            parse-opts          (-> (deep-merge opts (dissoc parse-keys :spec))
                                    (assoc :spec spec-with-args
                                           :restrict true)
                                    (update :exec-args
                                            (fnil merge {})
                                            exec-defaults))
            {:keys [args opts]} (if should-parse-args?
                                  (cli/parse-args (vec args) parse-opts)
                                  {:args (vec args) :opts {}})
            [arg & rest]        args]
        (if-let [subcmd-info (get cmd-info arg)]
          (recur (conj cmds arg) (merge all-opts opts) rest subcmd-info merged-spec)
          (if (:fn cmd-info)
            {:cmd-info  cmd-info
             :dispatch  cmds
             :opts      (merge all-opts opts)
             :args      args
             :cmd-tree  tree
             :bin-name  bin-name}
            (if arg
              {:error               :no-match
               :dispatch            cmds
               :wrong-input         arg
               :available-commands  (sort (filter string? (keys cmd-info)))
               :cmd-tree            tree
               :bin-name            bin-name}
              {:error               :input-exhausted
               :dispatch            cmds
               :available-commands  (sort (filter string? (keys cmd-info)))
               :cmd-tree            tree
               :bin-name            bin-name})))))))

(defn dispatch'
  "Alias for dispatch-tree (compat with possible naming)."
  [cmd-tree args opts]
  (dispatch-tree cmd-tree args opts))

;; prevents accidents when developing at the repl
(def ^:dynamic *exit?* false)

(defn exit
  ([] (exit 1))
  ([code]
   (if *exit?*
     (do
       (shutdown-agents)
       (System/exit code))
     (reset-errors!))))

;; -----------------------------------------------------------------------------
;; Middleware and runtime
;; -----------------------------------------------------------------------------

(defn ^:private print-error
  [s]
  (binding [*out* *err*]
    (println s))
  {:exit-code 1})

(defn dev-null-print-writer []
  (PrintWriter. "/dev/null"))

(defn wrap-with-help
  "Middleware: if --help is present, print help for the current dispatch path and do not run the command."
  [{:as res :keys [dispatch cmd-tree bin-name]}]
  (update-in res [:cmd-info :fn]
             (fn [f]
               (fn [{:as m :keys [opts]}]
                 (if (:help opts)
                   (do
                     (reset-errors!)
                     (help {:cmd-tree cmd-tree :args dispatch :bin-name bin-name})
                     {:exit-code 0})
                   (f m))))))

(defn wrap-with-quiet
  "Middleware: if --quiet, silence stdout/stderr while running the command."
  [res]
  (update-in res [:cmd-info :fn]
             (fn [f]
               (fn [{:as m :keys [opts]}]
                 (if (:quiet opts)
                   (binding [*out* (dev-null-print-writer)
                             *err* (dev-null-print-writer)]
                     (f m))
                   (f m))))))

(defn wrap-with-parse-error-reporting
  "Middleware: if parse errors were collected by :error-fn, print help for the
   closest valid command prefix, then print the errors and exit 1."
  [{:as res :keys [dispatch cmd-tree bin-name]}]
  (update-in res [:cmd-info :fn]
             (fn [f]
               (fn [m]
                 (if-let [errs (seq (errors))]
                   (do
                     (print-command-help cmd-tree dispatch bin-name)
                     (doseq [e errs]
                       (binding [*out* *err*]
                         (term/msg [:bold.error "Error: "] e)))
                     {:exit-code 1})
                   (f m))))))

(defn wrap-with-exit-code
  "Middleware: if the command returns {:exit-code n}, call System/exit, else pass result."
  [res]
  (update-in res [:cmd-info :fn]
             (fn [f]
               (fn [m]
                 (let [{:keys [exit-code] :as result} (f m)]
                   (if exit-code
                     (do (exit exit-code)
                         result)
                     result))))))

(defn wrap-with-debug
  "Middleware: if --debug, bind a dynamic term/*debug* flag for custom logging."
  [res]
  (update-in res [:cmd-info :fn]
             (fn [f]
               (fn [{:as m :keys [opts]}]
                 (if (:debug opts)
                   (binding [term/*debug* true]
                     (f m))
                   (f m))))))

(defmacro with-exception-reporting [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (when (try (parse-boolean (System/getenv "DEBUG")) (catch Exception _# false))
         (throw e#))
       (binding [*out* *err*]
         (term/print-error e#)
         (exit)))))

(defn print-program-info [cmd-tree {:keys [bin-name desc doc]}]
  (println desc)
  (println)
  (when doc
    (println doc)
    (println))
  (println "Usage:")
  (println (indent 2 (str bin-name " [flags]")))
  (println (indent 2 (str bin-name " [command]")))
  (println)
  (println "Available commands:")
  (println (indent 2 (subcommand-help-text cmd-tree [])))
  (print-command-flags cmd-tree [])
  (println)
  (println (format "Use \"%s [command] --help\" for more information about a command." bin-name)))

(defn dispatch
  "Top-level runner.
   - cmd-tree: nested command tree
   - args: vector of CLI strings
   - opts: babashka.cli parse options + {:middleware [...], :bin-name \"prog\"}
     If no :error-fn is provided, a default collector is used.

   Behavior:
   - Walks the tree parsing options progressively.
   - On error, prints suggestions/help.
   - On success, applies middleware and calls the selected :fn with the full context:
     {:cmd-info ..., :dispatch [...], :opts {...}, :args [...], :cmd-tree tree, :bin-name \"...\"}"
  [cmd-tree args {:keys [middleware] :as opts}]
  (let [opts        (if (:error-fn opts) opts (assoc opts :error-fn error-fn))
        {:as res
         :keys [error dispatch wrong-input available-commands]}
        (dispatch' cmd-tree args opts)]
    (if error
      (do
        (case error
          :input-exhausted (print-program-info cmd-tree opts)

          :no-match
          (let [sugs (candidates (or wrong-input "") available-commands 3)]
            (print-error
             (if (seq sugs)
               (str "Unknown command. Did you mean one of:\n"
                    (->> sugs
                         (map (fn [s] (str/join " " (concat dispatch [s]))))
                         (str/join "\n")
                         (indent 2)))
               (str "Unknown command. Available commands:\n\n"
                    (subcommand-help-text cmd-tree dispatch))))))
        ;; Print any parse errors after showing help
        (when-let [errs (seq (errors))]
          (doseq [e errs]
            (binding [*out* *err*]
              (term/msg [:bold.error "Error: "] e))))
        {:exit-code 1})
      (let [res' (reduce (fn [r m] (m r)) res (or middleware []))]
        ((get-in res' [:cmd-info :fn]) res')))))

(def default-middleware [wrap-with-debug
                         wrap-with-parse-error-reporting
                         wrap-with-help
                         wrap-with-quiet
                         wrap-with-exit-code])
