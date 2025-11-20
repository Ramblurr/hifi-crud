(ns hifi.dev.system
  (:require
   [zprint.core :as z]
   [hifi.util.terminal :as t]
   [hifi.core.main :as main]
   [hifi.dev.nrepl :refer [load-guardrails-silently]]
   [clojure.walk :as walk]
   [malli.core :as m]))

(defn pprint [coll & _rest]
  (let [opts {:style (t/zprint-style)}]
    (if (t/color?)
      (z/czprint coll opts)
      (z/zprint coll opts))))

(defn prune-system [sys]
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (:hifi/config-spec x))
       (if-let [name (some-> (:hifi/config-spec x) m/schema m/properties :name)]
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
  (case format
    :print  (-> (main/load-system opts)
                (prune-system)
                (pprint))
    :portal (portal-inspect (main/load-system opts))))
