;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2

(ns hifi.engine.impl
  (:require
   [hifi.engine.context :as context]
   [hifi.engine.interceptors-promesa :as ip]
   [promesa.exec.csp :as sp]
   [promesa.core :as pr]
   [exoscale.interceptor :as i]))

(defn assert-handler-return [result command]
  ;; command handlers can return
  ;; - nil
  ;; - a map
  (if (or (nil? result) (map? result))
    result
    (throw (context/ex ::invalid-handler-return {:result  result
                                                 :command command}))))

(def cmd-handler-interceptor
  {:interceptor/name :cmd-handler-interceptor
   :doc              "Calls the command handler with the coeffects and command data, then puts the outcome in the context"
   :enter            (fn cmd-handler-enter [ctx]
                       (let [command (:command ctx)
                             coeffects  (:coeffects ctx)
                             data    (:command/data command)
                             handler (context/get-command-handler ctx (:command/kind command))]
                         #_(tap> [:command-running command :coeffects coeffects :ctx ctx])
                         (if (some? handler)
                           (assoc ctx :outcome
                                  (-> (handler coeffects data)
                                      (assert-handler-return command)
                                      (assoc :outcome/command-id (:command/id command))))
                           (throw (context/ex ::no-cmd-handler {:command command
                                                                :ctx     ctx})))))})

(defn coeffect-interceptors-for [env command]
  (for [[coeffect-kind data] (context/get-command-coeffects env (:command/kind command))]

    (if-let [handler (context/get-coeffect-handler env coeffect-kind)]
      {:interceptor/name coeffect-kind
       :enter            (fn [ctx]
                           (update ctx :coeffects (partial handler ctx) data))}
      (throw (context/ex ::no-coeffect {:command       command
                                        :coeffect-kind coeffect-kind})))))

(defn resolve-interceptor [env int-name]
  (if-let [int (context/get-interceptor env int-name)]
    int
    (throw (context/ex ::no-interceptor {:interceptor/name     int-name
                                         :interceptor/registry (context/get-interceptor-names env)}))))

(defn resolve-interceptors [env maybe-interceptors]
  (map (fn [almost-int]
         (cond
           (keyword? almost-int) (resolve-interceptor env almost-int)
           (map? almost-int)     almost-int
           :else                 (context/ex ::invalid-interceptor {:interceptor almost-int}))) maybe-interceptors))

(defn with-interceptors [env command opts]
  (let [head   (:interceptors opts)
        middle (coeffect-interceptors-for env command)
        tail   [cmd-handler-interceptor]]
    (i/enqueue env
               (resolve-interceptors env (concat head middle tail)))))

(defn prepare-ctx [env command opts]
  (let [command {:command/kind (:command/kind command)
                 :command/id   (random-uuid)
                 :command/time (System/currentTimeMillis)
                 :command/data command}]
    (-> env
        (with-interceptors command opts)
        (assoc :command command))))

(defn merge-registries [r1 r2]
  (-> r1
      (update :commands merge (:commands r2))
      (update :coeffects merge (:coeffects r2))
      (update :interceptors merge (:interceptors r2))
      (update :effects merge (:effects r2))))

(def kinds {:command/kind     :commands
            :effect/kind      :effects
            :coeffect/kind    :coeffects
            :interceptor/name :interceptors})

(def kind-keys (into #{} (keys kinds)))

(defn compile-item [kind x]
  (case kind
    :command/kind     (update x :command/coeffects #(map (fn [coeffect]
                                                           (if (keyword? coeffect)
                                                             [coeffect nil]
                                                             coeffect)) %))
    :effect/kind      x
    :coeffect/kind    x
    :interceptor/name x))

(defn register-kind [env kind cmd-or-fx]
  (assoc-in env [:engine/registry (kind kinds) (kind cmd-or-fx)]
            (compile-item kind cmd-or-fx)))

(defn handle-command
  ([env command opts]
   (pr/handle
    (-> env
        (prepare-ctx command opts)
        ip/execute)
    (fn handle-results [ctx error]
      (if error
        (throw error)
        ctx
        #_(dissoc ctx
                  :exoscale.interceptor/queue
                  :exoscale.interceptor/stack
                  :engine/registry))))))

(defn handle-sync
  [{::keys [chan] :as env} opts]
  (let [!rv (atom nil)]
    #_{:clj-kondo/ignore [:loop-without-recur]}
    (pr/loop []
      (pr/handle
       (sp/take chan 1 ::timeout)
       (fn [command error]
         #_(tap> [:handle-sync :command command :error error])
         (cond
           (some? error)
           (do
             (sp/close! chan)
             (throw error))

           (nil? (#{::default ::timeout} command))
           (pr/handle
            (handle-command env command opts)
            (fn [iresult ierror]
              (if (some? ierror)
                (do
                  (sp/close! chan)
                  (throw ierror))
                (do
                  (swap! !rv
                         (fn [[_rv :as rv-wrapper] nv]
                           (if (nil? rv-wrapper)
                             [nv]
                             rv-wrapper))
                         iresult)
                  #_{:clj-kondo/ignore [:invalid-arity]}
                  (pr/recur)))))
           :else
           (do
             (sp/close! chan)
             (first @!rv))))))))

(defn dispatch-sync
  ([env command opts]
   (let [chan (sp/chan :buf 100)
         env  (assoc env ::chan chan)]
     (sp/put! chan command)
     (handle-sync env opts))))
