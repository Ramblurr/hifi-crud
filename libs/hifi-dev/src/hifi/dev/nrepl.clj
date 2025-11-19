(ns hifi.dev.nrepl
  (:require
   [hifi.repl :as repl]
   [clojure.repl :refer [demunge]]
   [clojure.string :as str]))

(when-not (resolve 'requiring-resolve)
  (throw (ex-info "hifi.dev requires at least Clojure 1.10"
                  *clojure-version*)))

(defn load-guardrails-silently []
  (let [gr-enabled? (System/getProperty "guardrails.enabled")
        report-info-var (try (requiring-resolve 'com.fulcrologic.guardrails.utils/report-info)
                             (catch Throwable _))]
    (when (and gr-enabled? report-info-var)
      (alter-var-root report-info-var (fn [_] (fn [_]))))
    (boolean (and gr-enabled? report-info-var))))

(defn- tap>log [frame level throwable message]
  (let [class-name (symbol (demunge (.getClassName frame)))]
    ;; only called for enabled log levels:
    (tap>
     (with-meta
       {:form     '()
        :level    level
        :result   (or throwable message)
        :ns       (symbol (or (namespace class-name)
                                                                        ;; fully-qualified classname - strip class:
                              (str/replace (name class-name) #"\.[^\.]*$" "")))
        :file     (.getFileName frame)
        :line     (.getLineNumber frame)
        :column   0
        :time     (java.util.Date.)
        :runtime  :clj}
       {:dev.repl/logging true}))))

(defn- ctl-log*adapter [log-star]
  (let [log*-fn (deref log-star)]
    (fn [logger level throwable message]
      (try
        (let [^StackTraceElement frame (nth (.getStackTrace (Throwable. "")) 2)]
          (tap>log frame level throwable message))
        (catch Throwable _))
      (log*-fn logger level throwable message))))

(defn jedi-time []
  (try
    (require 'jedi-time.core)
    true
    (catch Throwable _)))

(defn datomic-datafy []
  (try
    ((requiring-resolve 'datomic.dev.datafy/datafy!))
    true
    (catch Throwable _)))

(defn portal-clojure-logging []
  ;; if Portal and clojure.tools.logging are both present,
  ;; cause all (successful) logging to also be tap>'d:
  ;; hifi does not include clojure.tools.logging by default
  (try
    ;; if we have Portal on the classpath...
    (require 'portal.console)
    ;; ...then install a tap> ahead of tools.logging:
    (let [log-star (requiring-resolve 'clojure.tools.logging/log*)]
      (alter-var-root
       log-star
       (constantly (ctl-log*adapter log-star))))
    true
    (catch Throwable _)))

(defn pretty-exceptions []
  (try
    ((requiring-resolve 'clj-commons.pretty.repl/install-pretty-exceptions))
    true
    (catch Throwable _)))

(defn initializers []
  (reduce (fn [acc [kw msg init-fn]]
            (if (init-fn)
              (-> acc
                  (update :initiated conj kw)
                  (update :messages conj msg))
              acc))
          {:initiated #{}
           :messages []}
          [[:jedi-time "datafy jedi-time" jedi-time]
           [:datomic "datafy datomic" datomic-datafy]
           [:pretty-exceptions "pretty-exceptions" pretty-exceptions]
           [:portal-logging "clojure.tools.logging tap>'d" portal-clojure-logging]
           [:guardrails "guardrails" load-guardrails-silently]]))

(defn- available-middleware []
  (into []
        (filter #(try (requiring-resolve (nth % 2)) true (catch Throwable _)))
        [[:portal "portal" 'portal.nrepl/wrap-portal]
         [:cider "cider" 'cider.nrepl.middleware/cider-middleware]
         [:refactor-nrepl "refactor-nrepl" 'refactor-nrepl.middleware/wrap-refactor]]))

(def ^:private resolve-mw-xf
  (comp (map #(requiring-resolve %))
        (keep identity)))

(defn- handle-seq-var
  [var]
  (let [x @var]
    (if (sequential? x)
      (into [] resolve-mw-xf x)
      [var])))

(def ^:private mw-xf
  (comp
   (map #(nth % 2))
   (map symbol)
   resolve-mw-xf
   (mapcat handle-seq-var)))

(defn start-nrepl-server [{:keys [nrepl-port nrepl-bind] :as config
                           :or   {nrepl-port 0
                                  nrepl-bind "127.0.0.1"}}]
  (let [middleware     (available-middleware)
        middleware-kws (into #{} (map first) middleware)
        _              (println (str "hifi nREPL server middleware: " (when (seq middleware) (str/join ", " (map second middleware)))))
        _server        (repl/start-nrepl {:port                    nrepl-port
                                          :bind                    nrepl-bind
                                          :create-nrepl-port-file? true
                                          :middleware (into [] mw-xf middleware)})]
    (assoc config :with-middleware middleware-kws)))

(defn start-hifi-application [opts]
  ((requiring-resolve 'hifi.core.main/start) opts))

(defn main [opts]
  (let [{:keys [_initiated messages]} (initializers)]
    (when (seq messages)
      (println (str "hifi dev tooling enabled: " (str/join ", " messages))))
    (start-nrepl-server opts)
    (start-hifi-application opts)))
