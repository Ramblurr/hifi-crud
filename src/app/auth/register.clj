(ns app.auth.register
  (:require
   [app.ui.button :as btn]
   [app.ui.form :as form]
   [app.ui.icon :as icon]
   [exoscale.cloak :as cloak]
   [app.crypto :as crypto]
   [app.malli :as s]
   [app.forms :as forms]
   [hyperlith.core :as h]))

(def email-available? (partial forms/unique-attr-available? :user/email))

(defn EmailAvailable [db]
  [:fn {:error/message "that email is already registered"} #(email-available? db %)])

(defn RegisterForm [{:keys [db]}]
  [:and {:form/key :register}
   [:map
    [:email [:and ::s/email-address (EmailAvailable db)]]
    ;; [:email ::s/email-address] ;; uncomment this (and comment line above) to test how unique constraint violation errors are handled
    [:password ::s/secret]
    [:password2 ::s/secret]]
   [:fn {:error/message "passwords don't match"
         :error/path    [:password2]}
    (fn [{:keys [password password2]}]
      (=
       (cloak/unmask password)
       (cloak/unmask password2)))]])

(defn unique-conflict-error [data]
  (condp = (:attribute data)
    :user/email {:email "that email is already registered"}
    :else       {:_top "unknown unique conflict error"}))

(defn unhandled-tx-error [_error _data]
  {:_top "an error ocurred"})

(defn signals->tx [cofx signals]
  (let [{:keys [email password]} (forms/values-from-signals (RegisterForm nil) signals)]
    [{:user/id       (:new-user-id cofx)
      :user/email    email
      :user/keychain (crypto/create-user-keychain (:app/root-public-keychain cofx) password) ;; this is technically a side effect since it generates keys, but a good example of when to break the rules
      }]))

(defn tx-error->errors [e]
  (let [data (-> e Throwable->map :data)]
    (if (= :transact/unique (:error data))
      (unique-conflict-error data)
      (unhandled-tx-error e data))))

(defn validate-command
  "Handle registration form interactive validation"
  [cofx {:keys [signals]}]
  {:outcome/effects
   [(forms/validate-form (RegisterForm cofx) signals)]})

(defn submit-command
  "Handle registration form submission"
  [cofx {:keys [signals]}]
  (let [errors (forms/validate-form (RegisterForm cofx) signals :clear? false)]
    (if false
      {:outcome/effects [errors]}
      {:outcome/effects [{:effect/kind :db/transact
                          :effect/data {:tx-data  (signals->tx cofx signals)
                                        :signals  signals
                                        :on-error {:command/kind :register/tx-error
                                                   :signals      signals}}}]})))

(defn tx-error-command [_cofx {:keys [signals error] :as _data}]
  {:outcome/effects [(forms/merge-errors signals :register
                                         (tx-error->errors error)
                                         {:only :touched})]})

(defn render-register [{:keys [url-for]}]
  (let [form {:form/key         :register
              :submit-command   :register/submit
              :validate-command :register/validate
              :fields           {:email     ""
                                 :password  ""
                                 :password2 ""}}]
    (h/html
      [:main#morph.main
       [:div {:class "flex min-h-full flex-col justify-center py-12 sm:px-6 lg:px-8"}
        [:div {:class "sm:mx-auto sm:w-full sm:max-w-md"}
         [:a {:href (url-for :home)}
          [icon/Icon {::icon/name :rocket :class "mx-auto h-12 w-auto text-teal-600 fill-teal-900"
                      :alt        "Your Company"}]]
         [:h2 {:class "mt-6 text-center text-2xl/9 font-bold tracking-tight text-gray-900"}
          "Create an account"]]
        [:div {:class "mt-10 sm:mx-auto sm:w-full sm:max-w-[480px]"}
         [:div {:class "bg-white px-6 py-12 shadow-sm sm:rounded-lg sm:px-12"}
          [form/Form {::form/form form :data-indicator "inflight"}
           [:div.space-y-6
            [form/Input {::form/label       "Email"
                         ::form/form        form
                         ::form/description "Demo note: Use any email, no email confirmations are sent."
                         :type              :email
                         :autocomplete      "email"
                         :placeholder       "name@company.com"
                         :name              :email}]
            [form/Input {::form/label  "Password"
                         ::form/form   form
                         :type         :password
                         :autocomplete "new-password"
                         :name         :password}]
            [form/Input {::form/label  "Confirm Password"
                         ::form/form   form
                         :type         :password
                         :autocomplete "new-password"
                         :name         :password2}]
            [form/RootErrors {::form/form form}]
            [:div {:data-signals-spinning "false"}
             [btn/Button {::btn/priority      :primary
                          :type               "submit" :class "w-full"
                          :data-attr-disabled "$inflight"
                          :data-class         (format "{'spinning': $%s}" "inflight")}
              "Sign up"]]]]]

         [:p {:class "mt-10 text-center text-sm/6 text-gray-500"}
          "Already have an account? "
          [:a {:href (url-for :login) :class "font-semibold text-teal-600 hover:text-teal-500"}
           "Sign in"]]]]])))

(h/refresh-all!)
