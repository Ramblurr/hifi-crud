;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT


(ns app.effects
  (:require
   [hifi.engine.shell :as shell]
   [app.tab-state :as tab-state]
   [medley.core :as medley]
   [exoscale.cloak :as cloak]
   [hifi.engine.fx :as fx]
   [hifi.engine.context :as context]
   [hyperlith.core :as h]
   [hyperlith.extras.datahike :as d]))

(def print-fx
  {:effect/kind    :print
   :effect/handler (fn print-effect [_ctx data]
                     (tap> [:print-effect data])
                     (println data))})

(def sleep-fx
  {:effect/kind    :sleep
   :effect/handler (fn sleep-effect [_ctx {:keys [duration]}]
                     (Thread/sleep duration))})

(def d*-merge-signals-fx
  {:effect/kind    :d*/merge-signals
   :effect/handler (fn d*-merge-signals
                     [{:keys [!http-return] :as _ctx} data]
                     (swap! !http-return merge (h/signals data)))})

(def d*-redirect-fx
  {:effect/kind    :d*/redirect
   :effect/handler (fn d*-redirect
                     [{:keys [!http-return] :as _ctx} data]
                     (swap! !http-return  (fn [ret]
                                            (-> ret
                                                (merge (h/script (format "window.location = '%s'" data)))))))})

(def schedule-fx
  {:effect/kind    :app/schedule
   :effect/handler (fn schedule [ctx {:keys [command seconds]}]
                     (h/thread
                       (Thread/sleep (* 1000 seconds))
                       (try
                         (shell/dispatch-sync ctx command)
                         (catch Exception e
                           (tap> [:schedule-error e])))))})

(def db-transact-fx
  {:effect/kind    :db/transact
   :effect/handler (fn db-transact [{:keys [conn] :as ctx} {:keys [tx-data on-success on-error] :as data}]
                     (assert (some? conn) "No connection to Datahike")
                     (assert (vector? tx-data) "Datahike transaction data must be a vector")
                     (try
                       (let [r @(d/tx! conn tx-data)]
                         (when on-success
                           (fx/dispatch-command ctx (assoc on-success :tx-result r))))
                       (catch Exception e
                         (if on-error
                           (fx/dispatch-command ctx (assoc on-error :error e))
                           (throw e)))))})

(def tab-transact-fx
  {:effect/kind    :app/tab-transact
   :effect/handler (fn tab-transact [_ctx {:keys [tab-id tx-fn] :as data}]
                     (assert (some? tab-id) "No tab id provided")
                     (assert (fn? tx-fn) "Transaction function must be a function")
                     (tab-state/transact! tab-id tx-fn))})

(def db-input
  {:input/kind    :app/db
   :input/handler (fn db-input [{:keys [conn] :as _ctx} inputs _]
                    (assert (some? conn) "No connection to Datahike")
                    (assoc inputs :db (d/db conn)))})

(def root-public-keychain-input
  {:input/kind    :app/root-public-keychain
   :input/handler (fn root-public-keychain [ctx inputs _]
                    (assoc inputs :app/root-public-keychain
                           (:app/root-public-keychain ctx)))})

(defn current-user
  ([{:keys [sid conn]}]
   (current-user sid conn))
  ([sid conn]
   (some-> (d/find-by @conn :session/id sid '[:session/id {:session/user [:user/id :user/email]}])
           :session/user
           (assoc :user/role :app.role/user))))

(def current-user-input
  {:input/kind    :app/current-user
   :input/handler (fn current-user-input [{:keys [conn] :as ctx} inputs _]
                    (if-let [user (current-user (get-in ctx [:command :command/data :sid]) conn)]
                      (assoc inputs :app/current-user user)
                      inputs))})

(def restrict-roles-input
  {:input/kind    :app/restrict-roles
   :input/handler (fn restrict-roles-input [_ctx inputs required-roles]
                    (if-let [user (:app/current-user inputs)]
                      (if (required-roles (:user/role user))
                        inputs
                        (throw (ex-info "User not authorized" {:required-roles required-roles
                                                               :user           user})))

                      (throw (ex-info "User not found" {}))))})

(def squuid-input
  {:input/kind    :db/squuid
   :input/handler (fn squuid-input [_ctx inputs key]
                    (assoc inputs key (d/squuid)))})

(def tab-state-input
  {:input/kind    :app/tab-state
   :input/handler (fn squuid-input [ctx inputs _]
                    (assoc inputs :app/tab-state
                           (tab-state/tab-state!
                            (get-in ctx [:command :command/data :signals :tab-id]))))})

(def cloak-signals-interceptor
  {:interceptor/name :app/cloak-signals
   :doc              "Cloaks sensitive signal values in the command data"
   :enter            (fn [ctx]
                       (let [command-def       (context/get-command ctx (-> ctx :command :command/kind))
                             signal-name-paths (:app/cloak-signals command-def)]
                         (if signal-name-paths
                           (reduce (fn [ctx signal-path]
                                     (let [path (into [] (concat [:command :command/data :signals] signal-path))]
                                       (medley/update-existing-in ctx path cloak/mask)))
                                   ctx
                                   signal-name-paths)
                           ctx)))})

(defn effects [] [print-fx
                  d*-merge-signals-fx
                  db-transact-fx
                  d*-redirect-fx
                  tab-transact-fx
                  sleep-fx
                  schedule-fx

                  cloak-signals-interceptor
                  current-user-input
                  restrict-roles-input
                  squuid-input
                  root-public-keychain-input
                  db-input
                  tab-state-input])

(defn enrich-render [req]
  (-> req
      (assoc :db (d/db (:conn req)))
      (assoc :app/current-user (current-user req))))
