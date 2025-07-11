(ns hifi.dev-tasks.fmt
  (:require
   [cljfmt.main :as fmt]
   [babashka.tasks :refer [shell]]))

(defn fmt-main
  "Runs the cljfmt checker on all files"
  [& args]
  (apply fmt/-main args))
