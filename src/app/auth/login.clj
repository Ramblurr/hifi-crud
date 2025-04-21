(ns app.auth.login
  (:require
   [hyperlith.extras.datahike :as d]
   [exoscale.cloak :as cloak]
   [app.forms :as forms]
   [app.ui.form :as form]
   [app.ui :as ui]
   [hyperlith.core :as h]))

(defn hash [v]
  v)

(defn submit-login-command [{:keys [db] :as _cofx} {:keys [sid signals url-for]}]
  (let [values                    (forms/values-from-signals [:email :password] :login signals)
        {actual-pw-hash :user/password-hash
         user-id        :user/id} (d/find-by db :user/email (:email values) [:user/password-hash :user/id])]
    (if (= actual-pw-hash  (hash (-> values :password cloak/unmask)))
      {:outcome/effects [{:effect/kind :db/transact
                          :effect/data {:tx-data    [{:session/id   sid
                                                      :session/user [:user/id user-id]}]
                                        :on-success {:command/kind :login/tx-success
                                                     :redirect-to  (url-for :home)}
                                        :on-error   {:command/kind :login/tx-error
                                                     :signals      signals}}}]}
      {:outcome/effects [(forms/merge-errors signals :login
                                             {:_top "Invalid email or password"})]})))

(defn tx-success-command [_cofx data]
  {:outcome/effects [{:effect/kind :d*/redirect
                      :effect/data (:redirect-to data)}]})

(defn tx-error-command [_cofx {:keys [signals error] :as _data}]
  (tap> [:login-db-error error])
  {:outcome/effects [(forms/merge-errors signals :login
                                         {:_top "Login failed. Please try again later."})]})

(defn render-login [{:keys [url-for]}]
  (h/html
    (let [form {:form/key       :login
                :submit-command :login/submit
                :fields         {:email    ""
                                 :password ""}}]
      [:main#morph.main
       [:div
        {:class
         "flex min-h-full flex-col justify-center py-12 sm:px-6 lg:px-8"}
        [:div
         {:class "sm:mx-auto sm:w-full sm:max-w-md"}
         [:a {:href (url-for :home)}
          [::ui/icon {:ico/icon :rocket :class "mx-auto h-12 w-auto text-teal-600 fill-teal-900"
                      :alt      "Your Company"}]]
         [:h2 {:class "mt-6 text-center text-2xl/9 font-bold tracking-tight text-gray-900"}
          "Sign in to your account"]]
        [:div
         {:class "mt-10 sm:mx-auto sm:w-full sm:max-w-[480px]"}
         [:div {:class "bg-white px-6 py-12 shadow-sm sm:rounded-lg sm:px-12"}
          [::form/form {:form/form form}
           [:div.space-y-6
            [::form/input {:form/label   "Email"
                           :form/form    form
                           :type         :email
                           :autocomplete "email"
                           :name         :email}]
            [::form/input {:form/label   "Password"
                           :form/form    form
                           :type         :password
                           :autocomplete "current-password"
                           :name         :password}]

            [::form/errors {:form/form form}]
            [:div {:data-signals-spinning "false"}
             [::ui/button {:btn/priority :primary :type "submit" :class "w-full"}
              "Sign in"]]]]]
         [:p {:class "mt-10 text-center text-sm/6 text-gray-500"}
          "Not onboard yet? "
          [:a {:href (url-for :register) :class "font-semibold text-teal-600 hover:text-teal-500"}
           "Sign up"]]]]])))

(h/refresh-all!)
