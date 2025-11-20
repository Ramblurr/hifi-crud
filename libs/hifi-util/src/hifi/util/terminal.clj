(ns hifi.util.terminal
  (:require
   [clojure.string :as str]))

(defn color-theme
  "Determines the color theme used by hifi. One of :light or :dark. Defaults to :dark"
  []
  (let [theme (some-> (System/getenv "HIFI_THEME")
                      str/lower-case
                      str/trim)]
    (case theme
      "light" :light
      "dark" :dark
      :dark)))

(defn dark? []
  (= :dark (color-theme)))

(defn color? []
  (str/blank? (System/getenv "NO_COLOR")))

(defn zprint-style []
  (if (and (color?) (dark?))
    [:dark-color-map :community]
    [:community]))
