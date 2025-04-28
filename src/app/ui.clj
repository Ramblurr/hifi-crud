;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2


(ns app.ui
  (:require
   [app.ui.core :as uic]
   [hyperlith.impl.namespaces :refer [import-vars]]
   ;; import the chassis multimethods
   [app.ui.icon]
   [app.ui.button]
   [app.ui.form]))

;; HACK(hyperlith) shouldn't be using hyperlith.impl ns

(import-vars
 [app.ui.core
  cs
  raw?
  attr-map
  merge-attrs])
