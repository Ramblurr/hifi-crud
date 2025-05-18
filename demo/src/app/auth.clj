;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns app.auth
  (:require
   [app.auth.login :as login]
   [app.auth.logout :as logout]
   [app.auth.reset-password :as reset-password]
   [app.auth.forgot-password :as forgot-password]
   [app.auth.register :as register]))

(def pages
  {::login           {:path   "/login"
                      :render #'login/render-login}
   ::logout          {:path   "/logout"
                      :render #'logout/render-logout}
   ::register        {:path   "/register"
                      :render #'register/render-register}
   ::forgot-password {:path   "/forgot-password"
                      :render #'forgot-password/render-forgot-password}
   ::reset-password  {:path   "/reset-password"
                      :render #'reset-password/render-reset-password}})

(def register-validate-command
  {:command/kind      :register/validate
   :command/inputs    [:app/db]
   :command/handler   #'register/validate-command
   :app/cloak-signals #{[:register :password]
                        [:register :password2]}})

(def register-submit-command
  {:command/kind      :register/submit
   :command/inputs    [:app/db [:db/squuid :new-user-id] :app/root-public-keychain]
   :command/handler   #'register/submit-command
   :app/cloak-signals #{[:register :password]
                        [:register :password2]}})

(def register-tx-error-command
  {:command/kind    :register/tx-error
   :command/inputs  [:app/db]
   :command/handler #'register/tx-error-command})

(def login-submit-command
  {:command/kind      :login/submit
   :command/inputs    [:app/db]
   :command/handler   #'login/submit-login-command
   :app/cloak-signals #{[:login :password]}})

(def login-tx-success-command
  {:command/kind    :login/tx-success
   :command/inputs  []
   :command/handler #'login/tx-success-command})

(def login-tx-error-command
  {:command/kind    :login/tx-error
   :command/inputs  []
   :command/handler #'login/tx-error-command})

(def logout-submit-command
  {:command/kind    :logout/submit
   :command/inputs  [:app/current-user]
   :command/handler #'logout/submit-logout-command})

(def logout-tx-success-command
  {:command/kind    :logout/tx-success
   :command/inputs  []
   :command/handler #'logout/tx-success-command})

(defn commands []
  [register-validate-command
   register-submit-command
   register-tx-error-command
   login-submit-command
   login-tx-success-command
   login-tx-error-command
   logout-submit-command
   logout-tx-success-command])
