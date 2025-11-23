(ns hifi.dev.util)

(defn load-guardrails-silently []
  (let [gr-enabled? (System/getProperty "guardrails.enabled")
        report-info-var (try (requiring-resolve 'com.fulcrologic.guardrails.utils/report-info)
                             (catch Throwable _))]
    (when (and gr-enabled? report-info-var)
      (alter-var-root report-info-var (fn [_] (fn [_]))))
    (boolean (and gr-enabled? report-info-var))))
