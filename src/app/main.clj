(ns app.main
  (:gen-class)
  (:require [hyperlith.core :as h]
            [hyperlith.extras.datahike :as d]
            [engine.engine :as engine]))

(def css
  (h/static-css
   [["*, *::before, *::after"
     {:box-sizing :border-box
      :margin     0
      :padding    0}]

    [:html
     {:font-family "Arial, Helvetica, sans-serif"}]]))

(defn render-home [_req]
  (h/html
    [:link#css {:rel "stylesheet" :type "text/css" :href (css :path)}]
    [:main#morph.main
     [:div "Hello World"]]))

(def default-shim-handler
  (h/shim-handler
   (h/html
     [:link#css {:rel "stylesheet" :type "text/css" :href (css :path)}])))

(def router
  (h/router
   {[:get (css :path)] (css :handler)
    [:get  "/"]        default-shim-handler
    [:post "/"]        (h/render-handler #'render-home
                                         :on-open
                                         (fn [_req])
                                         :on-close
                                         (fn [_req]))}))

(defn ctx-start []
  (-> {}
      (d/ctx-start  "./db/dev.sqlite")))

(defn ctx-stop [ctx]
  (d/ctx-stop ctx))

(defn -main [& _]
  (h/start-app
   {:router         #'router
    :max-refresh-ms 200
    :ctx-start      ctx-start
    :ctx-stop       ctx-stop
    :csrf-secret    (h/env :csrf-secret)}))

;; Refresh app when you re-eval file
(h/refresh-all!)

(comment
  (-main)
  ;; (clojure.java.browse/browse-url "http://localhost:8080/")

  ;; stop server
  (((h/get-app) :stop)))
