(ns hifi.dev.system
  (:require
   [hifi.util.terminal :as term]
   [hifi.dev.util :refer [load-guardrails-silently]]))

(defn lazy-fn [symbol]
  (fn [& args] (apply (requiring-resolve symbol) args)))

(def czprint (lazy-fn 'zprint.core/czprint))
(def zprint (lazy-fn 'zprint.core/zprint))
(def load-system (lazy-fn 'hifi.core.main/load-system))
(def m-schema (lazy-fn 'malli.core/schema))
(def m-properties (lazy-fn 'malli.core/properties))
(def postwalk (lazy-fn 'clojure.walk/postwalk))

(defn pprint [coll & _rest]
  (let [opts {:style (term/zprint-style)}]
    (if (term/color?)
      (czprint coll opts)
      (zprint coll opts))))

(defn prune-system [sys]
  (postwalk
   (fn [x]
     (if (and (map? x) (:hifi/config-spec x))
       (if-let [name (some-> (:hifi/config-spec x) m-schema m-properties :name)]
         (assoc x :hifi/config-spec name)
         (dissoc x :hifi/config-spec))
       x))
   sys))

(defn portal-inspect [v]
  (reset! ((requiring-resolve 'portal.api/open)) v)
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. #((requiring-resolve 'portal.api/close))))
  (println "Press CTRL+C to exit")
  @(promise))

(defn inspect-system [{:keys [format] :or {format :portal} :as opts}]
  (load-guardrails-silently)
  (let [sys (term/with-spinner {:msg "Loading system" :disappear? true} (load-system opts))]
    (case format
      :print  (-> sys
                  (prune-system)
                  (pprint))
      :portal (portal-inspect sys))))
