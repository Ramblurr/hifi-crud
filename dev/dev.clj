(ns dev
  (:require
   [portal.api :as p]))

(set! *print-namespace-maps* false)

(p/open {:theme :portal.colors/gruvbox})
(add-tap portal.api/submit)
