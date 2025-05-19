;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns app.auth.login
  (:require
   [hifi.datastar :as datastar]
   [hifi.html :as html]
   [app.forms :as forms]
   [app.ui.button :as btn]
   [app.ui.form :as form]
   [app.ui.icon :as icon]
   [app.db :as d]
   [exoscale.cloak :as cloak]
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

(defn submit-login-command [{:keys [db] :as _cofx} {:keys [sid ::datastar/signals url-for]}]
  (assert db)
  (let [{:keys [email password]} (forms/values-from-signals [:email :password] :login signals)
        _                        (assert email)
        _                        (assert password)
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
                                                       :redirect-to  (url-for :app.home/home)}
                                          :on-error   {:command/kind :login/tx-error
                                                       :signals      signals}}}]}))))

(defn tx-success-command [_cofx data]
  {:outcome/effects [{:effect/kind :d*/redirect
                      :effect/data (:redirect-to data)}]})

(defn tx-error-command [_cofx {:keys [::datastar/signals error] :as _data}]
  (tap> [:login-db-error error _data])
  {:outcome/effects [(forms/merge-errors signals :login
                                         {:_top "Login failed. Please try again later."})]})

(defn render-login [{:keys [url-for]}]
  (let [form {:form/key       :login
              :submit-command :login/submit
              :fields         {:email    ""
                               :password ""}}]
    (html/->str
     [:main#morph.main
      #_[:style
         (html/raw "
button:disabled  {
  opacity: 0.5;
}

button > span {
  display: none;
}

button.spinning > span {
  display: inline;
}
")]
      #_[:form
         {:data-indicator "inflight2", :data-on-submit "@post('/wutf')"}
         [:button
          {:type               "submit",
           :data-attr-disabled "$inflight2",
           :data-class         "{spinning: $inflight2}"}
          [:span "Spinner"]
          "Click Me"]]
      [:div {:class "flex min-h-full flex-col justify-center py-12 sm:px-6 lg:px-8"}
       [:div {:class "sm:mx-auto sm:w-full sm:max-w-md"}
        [:a {:href (url-for :app.home/home)}
         [icon/Logotype {:class "mx-auto h-12 w-auto text-accent-foreground fill-accent-foreground"}]]
        [:h2 {:class "mt-6 text-center text-2xl/9 font-bold tracking-tight"}
         "Sign in to your account"]]
       [:div {:class "mt-10 sm:mx-auto sm:w-full sm:max-w-[480px]"}
        [:div {:class "bg-white px-6 py-12 shadow-sm sm:rounded-lg sm:px-12"}
         [form/Form {::form/form     form
                     :data-indicator "inflight"}
          [:div.space-y-6
           [form/Input {::form/label  "Email"
                        ::form/form   form
                        :autocomplete "email"
                        :name         :email}]
           [form/Input {::form/label  "Password"
                        ::form/form   form
                        :type         :password
                        :autocomplete "current-password"
                        :name         :password}]

           [form/RootErrors {::form/form form}]
           [:div
            [btn/Button {::btn/intent        :primary
                         :type               "submit" :class "w-full"
                         :data-attr-disabled "$inflight"
                         :data-class         (format "{'spinning': %s}" "$inflight")}
             "Sign in"]]]]]

        [:p {:class "mt-10 text-center text-sm/6 text-muted-foreground"}
         "Not onboard yet? "
         [:a {:href (url-for :app.auth/register) :class "link"}
          "Sign up"]]]]])))

(datastar/rerender-all!)
