(ns dev
  (:require
   [app.main :as app]
   [portal.api :as p]))

(p/open {:theme :portal.colors/gruvbox})
(add-tap portal.api/submit)

(defn reset []
  (app/stop)
  (app/start))

(comment

  (set! *print-namespace-maps* false)
  (clojure.repl.deps/sync-deps)
  ;;
  )
