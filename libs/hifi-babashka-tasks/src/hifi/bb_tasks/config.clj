(ns hifi.bb-tasks.config
  (:require
   [aero.core :as aero]))

(defn build-config []
  (let [f "config/build.edn"]
    (aero/read-config f)))
