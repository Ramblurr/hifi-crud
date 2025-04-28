;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2


(ns app.home
  (:require
   [app.home.index :as index]))

(def pages
  {:home {:path    "/"
          :handler #'index/render-home}})

(def hello-command
  {:command/kind    :home/hello
   :command/inputs  [:app/current-user [:app/restrict-roles #{:app.role/user}] :app/tab-state]
   :command/handler #'index/hello-command})

(def clear-notification-command
  {:command/kind    :home/clear-notification
   :command/inputs  [:app/current-user [:app/restrict-roles #{:app.role/user}]]
   :command/handler #'index/clear-notification-command})

(def admin-command
  {:command/kind    :home/admin
   :command/inputs  [:app/current-user [:app/restrict-roles #{:app.role/admin}]]
   :command/handler (fn [_ _])})

(defn commands []
  [hello-command
   clear-notification-command
   admin-command])
