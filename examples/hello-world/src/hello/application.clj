(ns hello.application
  (:require
   [hifi.config :as hifi.config]
   [hifi.http :as http]
   [hello.routes :as hello.routes]
   [hifi.core :as h :refer [extend-ns defplugin]]))

(defplugin hello-app
  "My application"
  {:hifi/routes (http/route-group {:routes hello.routes/routes
                                   :route-name ::app})})

(def components
  [hifi.http/defaults
   hello-app])

(defn config []
  (hifi.config/read-config :filename "examples/hello-world/resources/config.edn"))

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
   (-> @running-system_ :donut.system/instances  :hifi/http :hifi.http/root-handler meta :reitit.core/router))

  (do
    (stop)
    (require '[clj-reload.core :as clj-reload])
    (clj-reload/reload :all))
  (start)
  (hifi.config/set-env! :dev)
  ;;
  )
