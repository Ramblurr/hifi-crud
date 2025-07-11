;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns app.effects
  (:require
   [app.db :as d]
   [exoscale.cloak :as cloak]
   [hifi.datastar :as datastar]
   [hifi.datastar.tab-state :as tab-state]
   [hifi.engine.context :as context]
   [hifi.engine.fx :as fx]
   [hifi.engine.shell :as shell]
   [medley.core :as medley]
   [promesa.exec.csp :as sp]
   [starfederation.datastar.clojure.api :as d*]
   [taoensso.telemere :as t]))

(def print-fx
  {:effect/kind    :print
   :effect/handler (fn print-effect [_ctx data]
                     (println data))})

(def sleep-fx
  {:effect/kind    :sleep
   :effect/handler (fn sleep-effect [_ctx {:keys [duration]}]
                     (Thread/sleep duration))})

(def d*-patch-signals-fx
  {:effect/kind    :d*/patch-signals
   :effect/handler (fn d*-patch-signals
                     [{:keys [::datastar/sse-gen] :as _ctx} data]
                     (d*/patch-signals! sse-gen (datastar/edn->json data)))})

(def d*-redirect-fx
  {:effect/kind    :d*/redirect
   :effect/handler (fn d*-redirect
                     [{:keys [::datastar/sse-gen url-for] :as _ctx} {:keys [redirect-to]}]
                     (assert (keyword? redirect-to) "redirect-to must be a keyword")
                     (let [uri (url-for redirect-to)]
                       (when uri
                         (d*/patch-elements! sse-gen (str "<div id=\"hifi-on-load\" data-on-load=\"window.location = '" (url-for redirect-to) "'\">")))))})

(def schedule-fx
  {:effect/kind    :app/schedule
   :effect/handler (fn schedule [ctx {:keys [command seconds]}]
                     (sp/go
                       (Thread/sleep (* 1000 seconds))
                       (try
                         (shell/dispatch-sync ctx command)
                         (catch Exception e
                           (t/error! ::schedule-fx-error e)))))})

(def db-transact-fx
  {:effect/kind    :db/transact
   :effect/handler (fn db-transact [{:keys [conn] :as ctx} {:keys [tx-data on-success on-error] :as _data}]
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
   :effect/handler (fn tab-transact [_ctx {:keys [::datastar/tab-state-store ::datastar/tab-id tx-fn] :as data}]
                     (assert (some? tab-id) "No tab id provided")
                     (assert (fn? tx-fn) "Transaction function must be a function")
                     (tab-state/transact! tab-state-store tab-id tx-fn))})

(def db-coeffect
  {:coeffect/kind    :app/db
   :coeffect/handler (fn db-coeffect [{:keys [conn] :as _ctx} coeffects _]
                       (assert (some? conn) "No connection to Datahike")
                       (assoc coeffects :db (d/db conn)))})

(def root-public-keychain-coeffect
  {:coeffect/kind    :app/root-public-keychain
   :coeffect/handler (fn root-public-keychain [ctx coeffects _]
                       (assoc coeffects :app/root-public-keychain
                              (:app/root-public-keychain ctx)))})

(defn current-user
  ([{:keys [sid conn]}]
   (current-user sid conn))
  ([sid conn]
   (some-> (d/find-by (d/db conn) :session/id sid '[:session/id {:session/user [:user/id :user/email]}])
           :session/user
           (assoc :user/role :app.role/user))))

(def current-user-coeffect
  {:coeffect/kind    :app/current-user
   :coeffect/handler (fn current-user-coeffect [{:keys [conn] :as ctx} coeffects _]
                       (if-let [user (current-user (get-in ctx [:command :command/data :sid]) conn)]
                         (assoc coeffects :app/current-user user)
                         coeffects))})

(def restrict-roles-coeffect
  {:coeffect/kind    :app/restrict-roles
   :coeffect/handler (fn restrict-roles-coeffect [_ctx coeffects required-roles]
                       (if-let [user (:app/current-user coeffects)]
                         (if (required-roles (:user/role user))
                           coeffects
                           (throw (ex-info "User not authorized" {:required-roles required-roles
                                                                  :user           user})))

                         (throw (ex-info "User not found" {}))))})

(def squuid-coeffect
  {:coeffect/kind    :db/squuid
   :coeffect/handler (fn squuid-coeffect [_ctx coeffects key]
                       (assoc coeffects key (d/squuid)))})

(def tab-state-coeffect
  {:coeffect/kind    :app/tab-state
   :coeffect/handler (fn squuid-coeffect [ctx coeffects _]
                       (assoc coeffects :app/tab-state
                              (get-in ctx [:command :command/data ::datastar/tab-state])))})

(def cloak-signals-interceptor
  {:interceptor/name :app/cloak-signals
   :doc              "Cloaks sensitive signal values in the command data"
   :enter            (fn [ctx]
                       (let [command-def       (context/get-command ctx (-> ctx :command :command/kind))
                             signal-name-paths (:app/cloak-signals command-def)]
                         (if signal-name-paths
                           (reduce (fn [ctx signal-path]
                                     (let [path (into [] (concat [:command :command/data ::datastar/signals] signal-path))]
                                       (medley/update-existing-in ctx path cloak/mask)))
                                   ctx
                                   signal-name-paths)
                           ctx)))})

(defn effects [] [print-fx
                  d*-patch-signals-fx
                  db-transact-fx
                  d*-redirect-fx
                  tab-transact-fx
                  sleep-fx
                  schedule-fx

                  cloak-signals-interceptor
                  current-user-coeffect
                  restrict-roles-coeffect
                  squuid-coeffect
                  root-public-keychain-coeffect
                  db-coeffect
                  tab-state-coeffect])
