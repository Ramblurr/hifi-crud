;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns app.home.index
  (:require
   [hifi.html :as html]
   [hifi.util.crypto :as crypto]
   [hifi.datastar :as datastar]
   [app.ui.button :as btn]
   [app.ui.app-shell :as shell]
   [app.ui.core :as uic]))

(defn hello-command [{:app/keys [current-user tab-state] :as _cofx} {::datastar/keys [signals tab-id]}]
  (let [notif-id (str "toast" (crypto/new-uid))
        counter  (inc (:counter tab-state 0))
        notif    {:id    notif-id
                  :title "Hello"
                  :text  (format "Why hello there! You have clicked %d times" counter)}]
    {:outcome/effects [{:effect/kind :print
                        :effect/data (str "Hello there " (:user/email current-user) "!")}
                       {:effect/kind :app/tab-transact
                        :effect/data {:tab-id tab-id
                                      :tx-fn  #(-> %
                                                   (assoc :counter counter)
                                                   (update :notifications assoc notif-id notif))}}
                       {:effect/kind :app/schedule
                        :effect/data {:command {:command/kind :home/clear-notification
                                                :signals      {:tab-id          tab-id
                                                               :notification-id notif-id}}
                                      :seconds 10}}]}))

(defn clear-notification-command [_cofx {::datastar/keys [signals tab-id]}]
  {:outcome/effects [{:effect/kind :app/tab-transact
                      :effect/data {:tab-id tab-id
                                    :tx-fn  (fn [ts]
                                              (update ts :notifications
                                                      dissoc (-> signals :notification-id)))}}]})

(defn render-home-logged-out [{:keys [url-for] :as _req}]
  (let [link-cls "text-sm font-medium text-primary underline"]
    (html/compile
     [:main#morph.main
      [:div {:class "mx-auto max-w-3xl px-4 py-12 sm:px-6 lg:px-8"}
       [:div {:class "divide-y divide-gray-200 overflow-hidden rounded-lg bg-white shadow-sm"}
        [:div {:class "px-4 py-5 sm:px-6"}
         [:div {:class "sm:flex sm:items-center sm:justify-between"}
          [:div {:class "sm:flex sm:space-x-5"}
           [:div {:class "mt-4 text-center sm:mt-0 sm:pt-1 sm:text-left"}
            [:p {:class "text-xl font-bold text-gray-900 sm:text-2xl mt-2"}
             "You are not logged in"]
            [:a {:href (url-for :app.auth/login) :class link-cls} "Login"]
            " or "
            [:a {:href (url-for :app.auth/register) :class link-cls} "Sign Up"]]]]]]]])))

(defn button-gallery []
  [:div {:class "py-5 flex flex-col sm:flex-row sm:items-center gap-2 justify-end"}
   [btn/Button {:data-on-click (uic/dispatch :home/admin)}
    "Admin Action"]

   [btn/Button {::btn/intent :primary :data-on-click (uic/dispatch :home/hello)} "Hello Action"]
   [btn/Button {::btn/intent :destructive} "Destructive"]
   [btn/Button {::btn/intent :secondary} "Secondary"]
   [btn/Button {::btn/intent :secondary-destructive} "Secondary Destructive"]
   [btn/Button {::btn/intent :outline} "Outline"]
   [btn/Button {::btn/intent :outline-destructive} "Outline Destr"]

   [btn/Button {::btn/intent :ghost} "Ghost"]

   [btn/Button {::btn/intent :link} "Link"]
   [btn/Button {::btn/intent :link-destructive} "Link Destruct"]
   [btn/Button {::btn/intent :link-success} "Link Success"]])

(defn render-home-logged-in [req]
  (html/compile
   (shell/app-shell req
                    #_(lay/nav {:current-nav :dashboard})
                    [:div
                     [:div {:class "px-4 py-5 sm:p-6"}
                      [:p "You are logged in. You can take the following actions:"]
                       ;; [:pre {:data-json-signals true}]
                      [:div {:class "py-5 flex flex-col sm:flex-row sm:items-center gap-2 justify-end"}
                       [btn/Button {:data-on-click (uic/dispatch :home/admin)}
                        "Admin Action"]
                       [btn/Button {::btn/intent :primary :data-on-click (uic/dispatch :home/hello)} "Hello Action"]]]])))

(defn render-home [{:app/keys [current-user] :as req}]
  (html/->str
   (if (nil? current-user)
     (render-home-logged-out req)
     (render-home-logged-in req))))
