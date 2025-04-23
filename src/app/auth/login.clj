(ns app.auth.login
  (:require
   [hyperlith.extras.datahike :as d]
   [exoscale.cloak :as cloak]
   [app.forms :as forms]
   [app.ui.form :as form]
   [app.ui :as ui]
   [hyperlith.core :as h]
   [taoensso.tempel :as tempel]))

(defonce password-rate-limiter
  ;; Basic in-memory rate limiter to help protect against brute-force
  ;; attacks from the same user-id. In real applications you'll likely
  ;; want a persistent rate limiter for user-id, IP, etc.
  (tempel/rate-limiter
   {"1 attempt/s per 5 sec/s" [1        5000]
    "2 attempt/s per 1 min/s" [2 (* 1 60000)]
    "5 attempt/s per 5 min/s" [5 (* 5 60000)]}))

(defn user-keychain [db user-email]
  (:user/keychain
   (d/find-by db :user/email user-email [:user/keychain])))

(defn user-log-in
  "Attempts to log user in.

  Returns user's unencrypted secret `KeyChain` on success, or throws."
  [db user-email user-password]

  ;; Ensure a minimum runtime to help protect against timing attacks,
  ;; Ref. <https://en.wikipedia.org/wiki/Timing_attack>.
  (tempel/with-min-runtime 2000
    (if-let [rate-limited (password-rate-limiter user-email)]
      {:error :rate-limited :limit-info rate-limited}
      (let [encrypted-keychain (user-keychain db user-email)
            decrypted-keychain (when encrypted-keychain (tempel/keychain-decrypt encrypted-keychain
                                                                                 {:password (cloak/unmask user-password)}))]
        (if decrypted-keychain
          (do
            (password-rate-limiter :rl/reset user-email) ; Reset rate limiter
            {:ok       true
             :keychain decrypted-keychain})
          {:error :bad-login})))))

(defn submit-login-command [{:keys [db] :as _cofx} {:keys [sid signals url-for]}]
  (let [{:keys [email password]} (forms/values-from-signals [:email :password] :login signals)
        {:keys [error]}          (user-log-in db email password)]
    (if error
      {:outcome/effects [(forms/merge-errors signals :login
                                             {:_top
                                              (if (= error :rate-limited)
                                                "Too many attempts, please try again later"
                                                "Invalid email or password")})]}
      (let [{user-id :user/id} (d/find-by db :user/email email [:user/id])]
        {:outcome/effects [{:effect/kind :db/transact
                            :effect/data {:tx-data    [{:session/id   sid
                                                        :session/user [:user/id user-id]}]
                                          :on-success {:command/kind :login/tx-success
                                                       :redirect-to  (url-for :home)}
                                          :on-error   {:command/kind :login/tx-error
                                                       :signals      signals}}}]}))))

(defn tx-success-command [_cofx data]
  {:outcome/effects [{:effect/kind :d*/redirect
                      :effect/data (:redirect-to data)}]})

(defn tx-error-command [_cofx {:keys [signals error] :as _data}]
  (tap> [:login-db-error error])
  {:outcome/effects [(forms/merge-errors signals :login
                                         {:_top "Login failed. Please try again later."})]})

(defn render-login [{:keys [url-for]}]
  (let [form {:form/key       :login
              :submit-command :login/submit
              :fields         {:email    ""
                               :password ""}}]
    (h/html
      [:main#morph.main
       [:div {:class "flex min-h-full flex-col justify-center py-12 sm:px-6 lg:px-8"}
        [:div {:class "sm:mx-auto sm:w-full sm:max-w-md"}
         [:a {:href (url-for :home)}
          [::ui/icon {:ico/name :rocket :class "mx-auto h-12 w-auto text-teal-600 fill-teal-900"
                      :alt      "Your Company"}]]
         [:h2 {:class "mt-6 text-center text-2xl/9 font-bold tracking-tight text-gray-900"}
          "Sign in to your account"]]
        [:div {:class "mt-10 sm:mx-auto sm:w-full sm:max-w-[480px]"}
         [:div {:class "bg-white px-6 py-12 shadow-sm sm:rounded-lg sm:px-12"}
          [::form/form {:form/form      form
                        :data-indicator "inflight"}
           [:div.space-y-6
            [::form/input {:form/label   "Email"
                           :form/form    form
                           :type         :email
                           :autocomplete "email"
                           ;; :name         :email <-- name attribute forgotten
                           }]
            [::form/input {:form/label   "Password"
                           :form/form    form
                           :type         :password
                           :autocomplete "current-password"
                           :name         :password}]

            [::form/errors {:form/form form}]
            [:div {:data-signals-spinning "false"}
             [::ui/button {:btn/priority       :primary :type "submit" :class "w-full"
                           :data-attr-disabled "$inflight"
                           :data-class         (format "{'spinning': $%s}" "inflight")}
              "Sign in"]]]]]
         [:p {:class "mt-10 text-center text-sm/6 text-gray-500"}
          "Not onboard yet? "
          [:a {:href (url-for :register) :class "font-semibold text-teal-600 hover:text-teal-500"}
           "Sign up"]]]]])))

(h/refresh-all!)
