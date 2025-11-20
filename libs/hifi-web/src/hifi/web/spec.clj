;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.web.spec
  (:require
   [hifi.config :as config]))

(def Secret
  [:and
   [:fn {:error/message "should be a secret value"} config/secret?]
   [:fn {:error/message "should be a secret value that isn't nil"} config/present-secret?]])

(def HTTPKitServerOptions
  ;; ref https://github.com/http-kit/http-kit/blob/master/src/org/httpkit/server.clj
  [:map {:error/message "should be a map of HTTPServerOptions"}
   [:worker-pool {:doc "java.util.concurrent.ExecutorService or delay to use for handling requests" :optional true} :any]
   [:max-body {:doc "Max HTTP body size in bytes" :default (* 8 1024 1024) :optional true} pos-int?]
   [:max-ws {:doc "Max WebSocket message size in bytes" :default (* 4 1024 1024) :optional true} pos-int?]
   [:max-line {:doc "Max HTTP header line size in bytes" :default (* 8 1024) :optional true} pos-int?]
   [:proxy-protocol {:doc "Proxy protocol e/o #{:disable :enable :optional}" :optional true}
    [:enum :disable :enable :optional]]
   [:server-header {:doc "The \"Server\" header, disabled if nil" :default "http-kit" :optional true}
    [:maybe :string]]
   [:error-logger {:doc "Function to log errors (fn [msg ex])" :optional true} fn?]
   [:warn-logger {:doc "Function to log warnings (fn [msg ex])" :optional true} fn?]
   [:event-logger {:doc "Function to log events (fn [ev-name])" :optional true} fn?]
   [:event-names {:doc "Map of http-kit event names to loggable event names" :optional true}
    [:map-of :keyword :keyword]]
   [:address-finder {:doc "Function that returns java.net.SocketAddress for UDS support" :optional true} fn?]
   [:channel-factory {:doc "Function that takes java.net.SocketAddress and returns java.nio.channels.SocketChannel for UDS support" :optional true} fn?]])

(def HTTPServerOptions
  [:map {;; :error/message "should be a map of HTTPServerOptions"
         :name ::http-server}
   [:port {:doc "Which port to listen on for incoming requests"} pos-int?]
   [:host {:doc "Which IP to bind"} :string]
   [:http-kit {:optional true} HTTPKitServerOptions]])

(def DefaultHandlerOptions
  [:map
   [:not-found {:doc "404, when no route matches" :optional true} fn?]
   [:method-not-allowed {:doc "405, when no method matches" :optional true} fn?]
   [:not-acceptable {:doc "406, when handler returns nil" :optional true} fn?]])

(def RingHandlerOptions
  [:map {:error/message "should be a valid hifi.web.ring handler component options map"}
   [:default-handler-opts {:doc      "Options for the default handler"
                           :optional true} DefaultHandlerOptions]
   [:handler-opts
    {:doc           "The options map passed to the ring-handler function."
     :error/message "should be a valid ring handler component options map"
     :default       {:middleware [[:donut.system/ref [:hifi/middleware :hifi/assets]]
                                  [:donut.system/ref [:hifi/middleware :hifi/assets-resolver]]
                                  [:donut.system/ref [:hifi/middleware :exception-backstop]]]}}
    :map]])

(def RouterOptionsOptions
  [:map {:error/message "should be a map of options passed to reitit's router function after the route data"}
   [:route-data {:doc "Initial top-level route data" :default {}} :any]
   [:print-context-diffs? {:default false} :boolean]
   [:middleware-transformers {:doc     "A vector of middleware chain transformers"
                              :default []} [:vector fn?]]])
