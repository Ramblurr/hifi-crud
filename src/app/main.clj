(ns app.main
  (:gen-class)
  (:require
   [app.schema :as schema]
   [app.commands :as commands]
   [app.effects :as effects]
   [app.routes :as routes]
   [clojure.pprint :as pprint]
   [engine.shell :as shell]
   [hyperlith.core :as h]
   [hyperlith.extras.datahike :as d]))

(defn prepare-engine [ctx]
  (let [env   (shell/register
               [effects/effects commands/commands])
        extra (select-keys ctx [:conn])]
    (assert (some? (:conn extra)))
    (assoc ctx :engine
           (merge env extra))))

(defn ctx-start []
  (-> {}
      (d/ctx-start "./db/dev1.sqlite")
      (schema/ctx-start)
      (prepare-engine)))

(defn ctx-stop [ctx]
  (d/ctx-stop ctx))

(defn -main [& _]
  (h/start-app
   {:router         #'routes/router
    :max-refresh-ms 200
    :on-error       (fn [_ctx {:keys [error]}]
                      (tap> error)
                      (pprint/pprint error))
    :ctx-start      ctx-start
    :ctx-stop       ctx-stop
    :csrf-secret    (h/env :csrf-secret)}))

(defn start []
  (-main))

(defn stop []
  (((h/get-app) :stop)))

(h/refresh-all!)

(comment
  ;; (clojure.java.browse/browse-url "http://localhost:8080/")

  (start)
  (stop)
  ;; stop server
  (def conn (-> (h/get-app)
                :ctx
                :conn))

  (d/q '[:find (pull ?u [*])
         :in $ ?sid
         :where [?s :session/id ?sid]
         [?s :session/user ?u]]
       @conn "dh6ezrhvsz5t7vcbmRHUAt9GHM8")
  (d/find-by @conn :session/id "dh6ezrhvsz5t7vcbmRHUAt9GHM8" '[:session/id {:session/user [:user/email]}])
  (d/find-all @conn :session/id '[*])
  ;;
  )
