(ns hifi.dev-tasks.config
  (:require
   [aero.core :as aero]))

(defn build-config []
  (let [f "resources/env.edn"]
    (aero/read-config f)))
