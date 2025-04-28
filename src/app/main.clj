(ns app.main
  (:gen-class)
  (:require
   [app.tab-state :as tab-state]
   [app.schema :as schema]
   [app.commands :as commands]
   [app.effects :as effects]
   [app.routes :as routes]
   [clojure.pprint :as pprint]
   [engine.shell :as shell]
   [hyperlith.core :as h]
   [hyperlith.extras.datahike :as d]))

(def ctx-to-engine-keys
  "Keys in the app context that should be injected into the engine's environment"
  [:conn :app/root-public-keychain])

(defn prepare-engine [ctx]
  (let [env   (shell/register
               [effects/effects commands/commands])
        extra (select-keys ctx ctx-to-engine-keys)]
    (assert (some? (:conn extra)))
    (assoc ctx :engine
           (merge env extra))))

(defn ctx-start []
  (-> {}
      (d/ctx-start "./db/dev1.sqlite")
      (schema/ctx-start)
      (tab-state/ctx-start)
      (prepare-engine)))

(defn ctx-stop [ctx]
  (tab-state/ctx-stop ctx)
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
  (when-let [ctx (h/get-app)]
    ((ctx :stop))))

(h/refresh-all!)

(comment
  ;; (clojure.java.browse/browse-url "http://localhost:8080/")

  (start)
  (stop)
  ;; stop server
  (def conn (-> (h/get-app)
                :ctx
                :conn))
  (-> (h/get-app)
      :ctx)

  (do
    (def app (->  {}
                  (d/ctx-start  "./db/dev1.sqlite")
                  (schema/ctx-start)))
    (def conn (:conn app)))
  (do
    (d/ctx-stop app)
    (def conn nil))

  ;; Suck in demo data
  @(d/tx! conn
          (read-string (slurp "extra/data.tx")))

  @(d/tx! conn (take 1000 (read-string (slurp "extra/data.tx"))))
  @(d/tx! conn [{:session/id (str (random-uuid))}])

  @(d/tx! conn [[:db/retractEntity [:session/id "dh6ezrhvsz5t7vcbmRHUAt9GHM8"]]])

  (d/find-all @conn :invoice/id '[* {:invoice/customer [*]}])

  (d/find-all @conn :session/id '[*])
  (d/find-all @conn :user/id '[*])
  ;;
  )
