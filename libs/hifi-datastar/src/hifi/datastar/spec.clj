;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.datastar.spec)

(def signals :hifi.datastar/signals)
(def multicaster :hifi.datastar/multicaster)
(def <refresh-ch :hifi.datastar/<refresh-ch)
(def refresh-mult :hifi.datastar/refresh-mult)
(def <render :hifi.datastar/<render)
(def <cancel :hifi.datastar/<cancel)
(def render-fn :hifi.datastar/render-fn)
(def view-hash-fn :hifi.datastar/view-hash-fn)
(def error-report-fn :hifi.datastar/error-report-fn)
(def merge-fragment-opts :hifi.datastar/merge-fragment-opts)
(def first-render? :hifi.datastar/first-render?)

(def DatastarRenderMulticasterOptions
  [:map {:name ::render-multicaster}
   [:max-refresh-ms {:default 100
                     :doc     "Don't re-render clients more often than this many milliseconds"} :int]
   [:on-refresh {:optional true
                 :doc      "An arity-1 function called with the refresh event"} fn?]])
