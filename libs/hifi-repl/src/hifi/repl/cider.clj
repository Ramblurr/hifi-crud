(ns hifi.repl.cider
  (:require
   [cider.nrepl.middleware :as mw]))

(defn- resolve-or-fail [sym]
  (or (requiring-resolve sym)
      (throw (IllegalArgumentException. (format "Cannot resolve %s (type: %s)" sym (type sym))))))

(defn cider-middleware []
  (map resolve-or-fail mw/cider-middleware))
