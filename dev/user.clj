(ns user)

(comment
  (require '[hifi.dev.portal-helpers :as portal-repl]
           '[portal.colors]
           '[portal.api :as p])
  (p/open {:theme :portal.colors/gruvbox})
  (add-tap portal.api/submit)
  ;;
  )
