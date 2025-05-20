;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.datastar.spec
  (:import
   [java.time Duration Instant]))

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

(def DurationSchema [:fn #(instance? Duration %)])
(def InstantSchema [:fn #(instance? Instant %)])

(def DatastarRenderMulticasterOptions
  [:map {:name ::render-multicaster}
   [:max-refresh-ms {:default 100
                     :doc     "Don't re-render clients more often than this many milliseconds"} :int]
   [:on-refresh {:optional true
                 :doc      "An arity-1 function called with the refresh event"} fn?]])

(def TabStateComponentOptions
  [:map {:name ::tab-state}
   [:store-filename {:optional true
                     :doc      "Path to a file that the store will be persisted to upon JVM shutdown"} :string]
   [:clean-job-period {:default (Duration/ofSeconds 60)
                       :doc     "The period at which the tab-state clean job runs"} DurationSchema]
   [:clean-predicate {:optional true
                      :doc      "The predicate used to determine if a tab state is stale, defaults to an age/last-modified based test"} fn?]
   [:clean-age-threshold {:default (Duration/ofHours 12)
                          :doc     "Tab states which were last modified more than [[:clean-age-threshold]] ago are considered stale. Used with the default [[:clean-predicate]] function"}  DurationSchema]])
