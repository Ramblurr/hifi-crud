(ns app.main
  (:gen-class)
  (:require [hyperlith.core :as h]
            [hyperlith.extras.datahike :as d]
            [engine.shell :as shell]))

(def print-fx
  {:effect/kind    :print
   :effect/handler (fn print-effect [_ data]
                     (tap> [:print-effect data])
                     (println data))})

(def hello-command
  {:command/kind    :hello
   :command/inputs  []
   :command/handler (fn hello-command [_ _]
                      {:outcome/effects
                       [{:effect/kind :print
                         :effect/data "Hello!"}]})})

(def render-page-command
  {:command/kind    :render
   :command/inputs  []
   :command/handler (fn hello-command [_ _]
                      {:outcome/effects
                       [{:effect/kind :print
                         :effect/data "Hello!"}]})})

(def operations [print-fx hello-command])

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
    [:main#morph.main {:data-signals "{command: null}"}
     [:div "Hello World"]
     [:button {:data-on-click "$command = 'hello'; @post('/cmd')"}
      "Say Hello"]]))

(def default-shim-handler
  (h/shim-handler
   (h/html
     [:link#css {:rel "stylesheet" :type "text/css" :href (css :path)}])))

(defn action-dispatch-command [{:keys [engine body] :as req}]
  (let [command-name (keyword (:command body))]
    (if (shell/command engine command-name)
      (shell/dispatch-sync engine [command-name body])
      (throw (ex-info "Command not found" {:command command-name}))))
  nil)

(def router
  (h/router
   {[:get (css :path)] (css :handler)
    [:get  "/"]        default-shim-handler
    [:post "/"]        (h/render-handler #'render-home
                                         :on-open
                                         (fn [_req])
                                         :on-close
                                         (fn [_req]))
    [:post "/cmd"]     (h/action-handler #'action-dispatch-command)}))

(defn prepare-engine [ctx]
  (assoc ctx :engine
         (shell/register operations)))

(defn ctx-start []
  (-> {}
      (d/ctx-start "./db/dev.sqlite")
      (prepare-engine)))

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
