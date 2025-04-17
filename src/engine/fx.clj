(ns engine.fx
  "Built in effects"
  (:require
   [promesa.exec.csp :as sp]))

(def dispatch-fx
  {:effect/kind    :dispatch
   :effect/handler (fn dispatch-effect [ctx command]
                     (sp/put (:engine.impl/chan ctx) command)
                     :dispatched)})
