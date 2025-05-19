;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns app.system
  (:require
   [app.commands :as commands]
   [app.crypto :as crypto]
   [app.db :as d]
   [app.db.schema :as schema]
   [app.effects :as effects]
   [hifi.datomic.system :as datomic-sys]
   [hifi.engine.shell :as shell]
   [hifi.system.middleware :as hifi.mw]))

(def RootKeychainComponent
  {:donut.system/start  (fn [{config :donut.system/config}]
                          (crypto/unlock-root-keychain (:root-keychain-opts config) (:conn config)))
   :donut.system/config {:conn               [:donut.system/ref [:app/datomic :conn]]
                         :root-keychain-opts [:donut.system/ref [:env :app/components :root-keychain]]}})

(def FCISEngine
  {:donut.system/start  (fn [{config :donut.system/config}]
                          (let [env (shell/register
                                     [effects/effects commands/commands])]
                            (assert (some? (-> config :conn)))
                            (assert (some? (-> config :root-keychain :app/root-public-keychain)))
                            (assoc env
                                   :conn (-> config :conn)
                                   :app/root-public-keychain (-> config :root-keychain :app/root-public-keychain))))
   :donut.system/config {:conn          [:donut.system/ref [:app/datomic :conn]]
                         :root-keychain [:donut.system/local-ref [:root-keychain]]}})

(defn AppSystemDef []
  {:app/components  {:root-keychain RootKeychainComponent
                     :engine        FCISEngine}
   :app/datomic     (datomic-sys/DatomicComponentGroup {:component-group-key :app/datomic
                                                        :migration-data      schema/migrations})
   ;; this is merged with the default :hifi/middleware comp group
   :hifi/middleware {:app/middleware (hifi.mw/middleware-component
                                      {:name    :app/middleware
                                       :factory (fn [{:keys [conn engine]}]
                                                  (assert conn "No connection to Datomic")
                                                  (assert engine "No engine")
                                                  (fn [handler]
                                                    (fn extra-mw [req]
                                                      (handler
                                                       (assoc req
                                                              :app/conn conn
                                                              :app/db (d/db conn)
                                                              :app/engine engine
                                                              :app/current-user (effects/current-user (:sid req) conn))))))
                                       :donut.system/config
                                       {:conn   [:donut.system/ref [:app/datomic :conn]]
                                        :engine [:donut.system/ref [:app/components :engine]]}})}})
