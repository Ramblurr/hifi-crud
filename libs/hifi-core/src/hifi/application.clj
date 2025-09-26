(ns hifi.application
  (:require
   [donut.system :as ds]
   [hifi.core :as h :refer [defcallback deftemplate]]))

(defcallback plugins "The system plugins to load" ([] [=> :vector]))
(defcallback initialize "Build the system map" ([] [=> :map]))
(defcallback config "Load configuration" ([] [=> :map]))
(defcallback start "Start the application" ([] [=> :map]))
(defcallback stop "Stop the application" ([] [=> :map]))

(deftemplate
  (require '[donut.system]
           '[hifi.config]
           '[hifi.core]
           '[hifi.core.system])

  (defonce running-system_ (atom nil))

  (hifi.core/defn-default ::marker config
    "Load configuration"
    []
    (hifi.config/read-config))

  (hifi.core/defn-default ::marker initialize []
    (hifi.core.system/build-system (config) plugins))

  (hifi.core/defn-default ::marker start
    "Start the application"
    []
    (let [i (initialize)
          s (donut.system/start i)]
      (reset! running-system_ s)
      s))

  (hifi.core/defn-default ::marker stop
    "Stop the application"
    []
    (when @running-system_
      (reset! running-system_ (donut.system/stop @running-system_)))))

(comment
  (do
    (remove-ns 'hifi.backtick)
    (remove-ns 'hifi.core)
    (remove-ns 'hifi.application)
    (remove-ns 'hello.application))
  ;; rcf
  )
