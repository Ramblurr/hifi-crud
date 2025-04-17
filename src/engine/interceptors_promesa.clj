(ns engine.interceptors-promesa
  "Implement exoscale.interceptor protocols for promesa"
  (:require
   [promesa.exec.csp :as sp]
   [promesa.core :as pr]
   [exoscale.interceptor.protocols :as prot]
   [exoscale.interceptor.impl :as impl]))

(extend-protocol prot/AsyncContext
  java.util.concurrent.CompletableFuture
  (then [p f] (pr/then p f))
  (catch [p f] (pr/catch p f)))

(extend-protocol prot/InterceptorContext
  java.util.concurrent.CompletableFuture
  (async? [_] true))

(defn execute
  "Like `exoscale.interceptor/execute` but ensures we always get a
  CompletableFuture back"
  ([ctx interceptors]
   (execute (impl/enqueue ctx interceptors)))
  ([ctx]
   (let [p (pr/deferred)]
     (impl/execute ctx
                   #(pr/resolve! p %)
                   #(pr/reject! p %))
     p)))
