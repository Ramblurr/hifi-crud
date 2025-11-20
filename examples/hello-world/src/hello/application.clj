(ns hello.application
  (:require
   [hifi.assets :as assets]
   [hifi.config :as hifi.config]
   [hifi.web :as http]
   [hello.routes :as hello.routes]
   [hifi.core :as h :refer [extend-ns defplugin]]))

(defplugin hello-app
  "My application"
  {:hifi/routes (http/route-group hello.routes/app)})

(def plugins
  [hifi.web/Defaults
   hifi.assets/Pipeline
   hello-app])

(defn config []
  (hifi.config/read-config :filename "examples/hello-world/hifi.edn"))

(extend-ns '[hifi.application :opts {:some-opt "a value"}])

(comment
  (clojure.repl/doc config)
  (ns-unmap *ns* 'stop)
  (remove-ns 'hello.application)
  (start)
  (stop)
  (config)
  (initialize)

  (reitit.core/routes
   (-> @running-system_ :donut.system/instances  :hifi/web :hifi.web/root-handler meta :reitit.core/router))

  (config)
  (do
    (stop)
    (require '[clj-reload.core :as clj-reload])
    (clj-reload/reload :all)
    (start))
  (hifi.config/set-env! :dev)
  ;;
  )
