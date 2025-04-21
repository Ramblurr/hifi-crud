(ns app.commands
  (:require
   [app.auth :as auth]
   [app.home :as home]))

(defn commands []
  [home/commands
   auth/commands])
