;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns dev
  (:require
   [app.main :as app]
   [app.ui.core :as ui-core]
   [clj-reload.core :as clj-reload]
   [hifi.system :as hifi]))

;; --------------------------------------------------------------------------------------------
;; Toggle Dev-time flags

(set! *warn-on-reflection* true)
(set! *print-namespace-maps* false)
(ui-core/enable-opts-validation!)

;; --------------------------------------------------------------------------------------------
;; System Control

;; Configure the paths containing clojure sources we want clj-reload to reload
(clj-reload/init {:dirs      ["src" "dev" "test"]
                  :no-reload #{'user 'dev}})

(defn restart
  "Restart the application system."
  []
  (hifi/stop @app/system)
  (app/start))

(defn reset
  "Reset the application system by stopping the system, reloading all code, then starting the system again."
  [& args]
  (hifi/stop @app/system)
  (apply clj-reload/reload args)
  (app/start))

(comment

  ;;; System Control
  ;; Restart the system
  (restart)
  ;; Reset the system
  ;; A reset is a stop, code reload, and start.
  (reset)
  ;; You can pass options to clj-reload to control the reload behavior.
  (reset {:only :all})

  ;;; Adding/Modifying Dependencies in deps.edn
  ;; If you add or modify your dependencies, you can run this to sync them.
  ;; This will save you a REPL restart in most cases.
  (clojure.repl.deps/sync-deps)
  ;;
  )
