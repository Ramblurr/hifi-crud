;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT


(ns engine.fx
  "Built in effects"
  (:require
   [promesa.exec.csp :as sp]))

(defn dispatch-command [ctx command]
  (sp/put! (:engine.impl/chan ctx) command)
  :dispatched)

(def dispatch-fx
  {:effect/kind    ::dispatch
   :effect/handler dispatch-command})
