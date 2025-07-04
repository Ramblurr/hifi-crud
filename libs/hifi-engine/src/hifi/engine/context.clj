;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2


(ns hifi.engine.context)

(defn get-effectuator [ctx fx-id]
  (get-in ctx [:engine/registry :effects fx-id :effect/handler]))

(defn get-effects [ctx]
  (get-in ctx [:outcome :outcome/effects]))

(defn get-coeffect-handler [ctx coeffect-kind]
  (get-in ctx [:engine/registry :coeffects coeffect-kind :coeffect/handler]))

(defn get-command-coeffects [ctx cmd-kind]
  (get-in ctx [:engine/registry :commands cmd-kind :command/coeffects]))

(defn get-command [ctx cmd-kind]
  (get-in ctx [:engine/registry :commands cmd-kind]))

(defn get-command-handler [ctx cmd-kind]
  (get-in ctx [:engine/registry :commands cmd-kind :command/handler]))

(defn get-interceptor-names [ctx]
  (keys (get-in ctx [:engine/registry :interceptors])))

(defn get-interceptor [ctx int-name]
  (get-in ctx [:engine/registry :interceptors int-name]))

(defn ex [kw data]
  (ex-info (str kw) (assoc data :error/kind kw)))
