(ns user)

(comment
  (do
    (require '[hifi.dev.portal-helpers :as portal-repl]
             '[hifi.config]
             '[portal.colors]
             '[portal.api :as p])
    (def transforms portal-repl/recommended-transforms)
    (def tap-routing nil)
    (defonce my-submit (portal-repl/make-submit :transforms #'transforms :tap-routing #'tap-routing))
    (p/open {:theme :portal.colors/gruvbox})
    (add-tap my-submit))

  (clojure.repl.deps/sync-deps)

  (do
    (hifi.core/set-env! :dev)
    (require '[clj-reload.core :as clj-reload])
    (clj-reload/init {:dirs
                      ["examples/hello-world/src"
                       "libs/hifi-config/src"
                       "libs/hifi-core/src"
                       "libs/hifi-assets/src"
                       "libs/hifi-datastar/src"
                       "libs/hifi-datomic/src"
                       "libs/hifi-dev/src"
                       "libs/hifi-engine/src"
                       "libs/hifi-error/src"
                       "libs/hifi-html/src"
                       "libs/hifi-logging/src"
                       "libs/hifi-system/src"
                       "libs/hifi-util/src"]
                      :no-reload #{'user 'dev}}))
  (clj-reload/reload :all)
  (System/getProperty "guardrails.enabled")
  (System/setProperty "guardrails.enabled" "")

  (let [c (hifi.config/read-config "/home/ramblurr/src/clojure-playground/hyperlith-is-for-crud/libs/hifi-cli/new-test/config/hifi.edn" {:profile :dev})
        plugs (into [] (butlast (:hifi/plugins c)))]

    (hifi.core.system/build-system (hifi.core.system/resolve-plugins plugs) c))

  (hifi.core.mai)
  (->> (or  [])
       (system/resolve-plugins)
       (system/build-system config))

  ;;
  )
