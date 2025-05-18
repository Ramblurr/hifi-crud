;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns app.commands
  (:require
   [app.auth :as auth]
   [app.home :as home]))

(defn commands []
  [home/commands
   auth/commands])
