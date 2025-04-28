;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT

(ns hifi.engine.shell
  "It's not the core, it's the shell"
  (:require
   [hifi.engine.context :as context]
   [hifi.engine.fx :as fx]
   [hifi.engine.interceptors :as int]
   [hifi.engine.impl :as impl]))

(def default-global-interceptors
  "The default set of interceptors that executes effects"
  [:unhandled-error-interceptor :do-fx-interceptor])

(def minimal-global-interceptors
  "A minimal set of interceptors that does not execute effects."
  [:unhandled-error-interceptor])

(def default-opts {:interceptors default-global-interceptors})

(def default-registry
  {:engine/registry {:commands     {}
                     :effects      {::fx/dispatch fx/dispatch-fx}
                     :inputs       {}
                     :interceptors {:unhandled-error-interceptor int/unhandled-error-interceptor
                                    :do-fx-interceptor           int/do-fx-interceptor}}})

(defn command
  "Get a command definition from the registry"
  [env cmd-name]
  (context/get-command env cmd-name))

(defn register
  "Register a command, effect or input definition, returns a dispatch environment.

  command: adds a single command to the registry
  input: adds a single input to the registry
  effect: adds a single effect to the registry
  interceptor: adds a single interceptor to the registry

  sequential: adds all of the items in the seq to the registry, they should be one of the above
  map: assumes it is a another env map and merges the args into it
  fn: assumes it is a 0-arity thunk and invokes it to retrieve more operations
  "
  ([item-or-items]
   (register default-registry item-or-items))
  ([env item-or-items-or-env]
   (cond
     (map? item-or-items-or-env)
     (if-let [kind (some #(impl/kind-keys %) (keys item-or-items-or-env))]
       (impl/register-kind env kind item-or-items-or-env)
       (impl/merge-registries env item-or-items-or-env))

     (sequential? item-or-items-or-env)
     (reduce register env item-or-items-or-env)

     (fn? item-or-items-or-env)
     (reduce register env (item-or-items-or-env))

     :else
     (throw (ex-info "Invalid type to register" {:args item-or-items-or-env})))))

(defn dispatch-sync
  "Synchronously (immediately) process command, returns a CompleteableFuture that resolves to the result.

  `command` is a map containing at least a :command/kind keyword identifying the command type"
  ([env command]
   (dispatch-sync env command default-opts))
  ([env command opts]
   @(impl/dispatch-sync env command opts)))
