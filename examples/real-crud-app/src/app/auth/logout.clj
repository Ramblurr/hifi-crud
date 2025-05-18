;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns app.auth.logout
  (:require
   [hifi.datastar :as datastar]
   [hifi.html :as html]
   [app.ui.core :as uic]))

(defn submit-logout-command [{:keys [:app/current-user] :as _cofx} {:keys [sid url-for]}]
  (when current-user
    (let [user-id (:user/id current-user)]
      {:outcome/effects [{:effect/kind :db/transact
                          :effect/data {:tx-data    [[:db/retract [:session/id sid] :session/user [:user/id user-id]]]
                                        :on-success {:command/kind :logout/tx-success
                                                     :redirect-to  (url-for :app.auth/login)}}}]})))

(defn tx-success-command [_cofx data]
  {:outcome/effects [{:effect/kind :d*/redirect
                      :effect/data (:redirect-to data)}]})

(defn render-logout [_]
  (html/->str
   [:main#morph.main
    [:div {:data-on-load (uic/dispatch :logout/submit)}]]))

(datastar/rerender-all!)
