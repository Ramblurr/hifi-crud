(ns app.home.index
  (:require
   [app.ui :as ui]
   [app.ui.core :as uic]
   [hyperlith.core :as h]))

(defn hello-command [{:app/keys [current-user tab-state] :as _cofx} {:keys [signals]}]
  (let [notif-id (h/new-uid)
        counter  (inc (:counter tab-state 0))
        notif    {:id    notif-id
                  :title "Hello"
                  :text  (format "Why hello there! You have clicked %d times" counter)}]
    {:outcome/effects [{:effect/kind :print
                        :effect/data (str "Hello there " (:user/email current-user) "!")}
                       {:effect/kind :app/tab-transact
                        :effect/data {:tab-id (:tab-id signals)
                                      :tx-fn  #(-> %
                                                   (assoc :counter counter)
                                                   (update :notifications assoc notif-id notif))}}
                       {:effect/kind :app/schedule
                        :effect/data {:command {:command/kind :home/clear-notification
                                                :signals      {:tab-id          (:tab-id signals)
                                                               :notification-id notif-id}}
                                      :seconds 5}}]}))

(defn clear-notification-command [_cofx {:keys [signals]}]
  {:outcome/effects [{:effect/kind :app/tab-transact
                      :effect/data {:tab-id (-> signals :tab-id)
                                    :tx-fn  (fn [ts]
                                              (update ts :notifications
                                                      dissoc (-> signals :notification-id)))}}]})

(defn notification [id title text]
  [:div {:id id :class "notification-transition pointer-events-auto w-full max-w-sm overflow-hidden rounded-lg bg-white shadow-lg ring-1 ring-black/5"}
   [:div {:class "p-4"}
    [:div {:class "flex items-start"}
     [:div {:class "shrink-0"}
      [::ui/icon {:ico/icon :check-circle :class "size-6 text-green-400"}]]
     [:div {:class "ml-3 w-0 flex-1 pt-0.5"}
      [:p {:class "text-sm font-medium text-gray-900"}
       title]
      [:p {:class "mt-1 text-sm text-gray-500"}
       text]]
     [:div {:class "ml-4 flex shrink-0"}
      [:button {:type          "button" :class "inline-flex rounded-md bg-white text-gray-400 hover:text-gray-500 focus:ring-2 focus:ring-teal-500 focus:ring-offset-2 focus:outline-hidden"
                :data-on-click (format "$notification-id = '%s'; %s" id (uic/dispatch :home/clear-notification))}
       [:span {:class "sr-only"} "Close"]
       [::ui/icon {:ico/icon :x :class "size-5"}]]]]]])

(defn notification-region [notifications]
  [:div {:aria-live               "assertive"
         :class                   "pointer-events-none fixed inset-0 flex items-end px-4 py-6 sm:items-start sm:p-6"
         :data-signals__ifmissing "{'notification-id': null}"}
   [:div {:id "notification-container" :class "flex w-full flex-col items-center space-y-4 sm:items-end"}
    (for [notif notifications]
      (let [{:keys [id title text]} notif]
        (notification id title text)))]])

(defn render-home-logged-out [{:keys [url-for] :as req}]
  (let [link-cls "text-sm font-medium text-teal-600 underline"]
    [:main#morph.main
     [:div {:class "mx-auto max-w-3xl px-4 py-12 sm:px-6 lg:px-8"}
      [:div {:class "divide-y divide-gray-200 overflow-hidden rounded-lg bg-white shadow-sm"}
       [:div {:class "px-4 py-5 sm:px-6"}
        [:div {:class "sm:flex sm:items-center sm:justify-between"}
         [:div {:class "sm:flex sm:space-x-5"}
          [:div {:class "mt-4 text-center sm:mt-0 sm:pt-1 sm:text-left"}
           [:p {:class "text-xl font-bold text-gray-900 sm:text-2xl mt-2"}
            "You are not logged in"]
           [:a {:href (url-for :login) :class link-cls} "Login"]
           " or "
           [:a {:href (url-for :register) :class link-cls} "Sign Up"]]]]]]]]))

(defn render-home [{:app/keys [current-user tab-state] :as req}]
  (h/html
    (if (nil? current-user)
      (render-home-logged-out req)
      [:main#morph.main {:data-signals-tab-id__case.kebab (format "'%s'" (:app/tab-id req))}
       [:div {:class "mx-auto max-w-3xl px-4 py-12 sm:px-6 lg:px-8"}
        [:div {:class "divide-y divide-gray-200 overflow-hidden rounded-lg bg-white shadow-sm"}
         [:div {:class "px-4 py-5 sm:px-6"}
          [:div {:class "sm:flex sm:items-center sm:justify-between"}
           [:div {:class "sm:flex sm:space-x-5"}
            [:div {:class "mt-4 text-center sm:mt-0 sm:pt-1 sm:text-left"}
             [:p {:class "text-sm font-medium text-gray-600"} "Welcome back,"]
             [:p
              {:class "text-xl font-bold text-gray-900 sm:text-2xl"}
              (:user/email current-user)]]]
           [:div {:class "mt-5 flex justify-center sm:mt-0"}
            [::ui/button {:btn/priority  :secondary-destructive
                          :data-on-click (uic/dispatch :logout/submit)}
             "Logout"]]]]
         (when current-user
           [:div {:class "px-4 py-5 sm:p-6"}
            [:p "You are logged in. You can take the following actions:"]
            [:div {:class "py-5 flex items-center gap-2 justify-end"}
             [::ui/button {:data-on-click (uic/dispatch :home/admin)}
              "Admin Action"]
             [::ui/button {:btn/priority :primary :data-on-click (uic/dispatch :home/hello)}
              "Hello Action"]]])]]
       (notification-region
        (vals (:notifications tab-state)))])))

(h/refresh-all!)
