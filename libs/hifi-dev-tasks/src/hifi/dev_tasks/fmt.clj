(ns hifi.dev-tasks.fmt
  (:require
   [babashka.tasks :refer [shell]]))

(defn fmt-main
  "Runs the cljfmt checker on all files"
  [& _]
  (shell "cljfmt check"))

(defn fmt-fix-main
  "Runs cljfmt fixer on all files"
  [& _]
  (shell "cljfmt fix ."))
