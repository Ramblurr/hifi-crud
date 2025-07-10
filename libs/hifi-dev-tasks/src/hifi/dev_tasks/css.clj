;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.css
  (:require
   [hifi.dev-tasks.css.tailwind :as tailwind]))

(defn build
  "Build the production CSS assets."
  []
  (when (tailwind/using-tailwind?)
    (tailwind/build-prod)))
