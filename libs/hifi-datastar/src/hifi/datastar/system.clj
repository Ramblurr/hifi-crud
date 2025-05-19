(ns hifi.datastar.system
  (:require
   [hifi.datastar.spec :as spec]
   [hifi.datastar.multicast :as mult]))

(def DatastarRenderMulticasterComponent
  "A donut.system component for the datastar render multicaster
  Config:
    - :hifi/options - See DatastarRenderMulticasterOptions"
  {:donut.system/start  (fn  [{{:keys [:hifi/options]} :donut.system/config}]
                          ;; Use of delay is to workaround https://github.com/donut-party/system/issues/43
                          ;; We use
                          (let [multicaster (mult/start-render-multicaster options)]
                            (delay multicaster)))
   :donut.system/stop   (fn [{instance :donut.system/instance}]
                          (mult/stop-render-multicaster @instance))
   :donut.system/config {}
   :hifi/options-schema spec/DatastarRenderMulticasterOptions
   :hifi/options-ref    [:hifi/components :options :datastar-render-multicaster]})
