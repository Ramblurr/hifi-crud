(ns hifi.datastar
  {:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
  (:require
   [charred.api :as charred]
   [clojure.tools.logging :as log]
   [hifi.datastar.multicast :as mult]
   [hifi.datastar.spec :as spec]
   [hifi.datastar.tab-state :as tab-state]
   [hifi.util.assets :as assets]
   [hifi.util.codec :as codec]
   [promesa.exec.csp :as sp]
   [starfederation.datastar.clojure.brotli :as brotli]
   [starfederation.datastar.clojure.adapter.common :as d*com]
   [starfederation.datastar.clojure.api :as d*])
  (:import
   [java.io StringWriter]))

(def default-write-json (charred/write-json-fn {}))

(defn write-json-str
  "Write json to a string.  See options for [[write-json]]."
  [data & {:as args}]
  (let [w (StringWriter.)]
    (default-write-json w data)
    (.toString w)))

(def edn->json write-json-str)

(defmacro try-log [report data & body]
  `(try
     ~@body
     (catch Throwable ~'t
       (~report ~'t ~data)
       ;; Return nil when there is an error
       nil)))

(defn long-lived-render-setup
  "Creates a context map for an async long-lived render handler.
  - render-fn - arity/1 function of req to render the view, must return a string
  - view-hash-fn - arity/0 function to generate a hash of the view, must return a string, default base64 of (hash view)
  - error-report-fn - arity/2 function of throwable and req to report errors, default: prn
  - patch-elements-opts - a map of options to pass to the d* sdks patch-elements see [[starfederation.datastar.clojure.api/patch-elements!]]
  - multicaster - the value returned by [[start-render-multicaster]] (if nil, :hifi.datastar/multicaster is expected in the `req`)

  Returns a map to pass to [[long-lived-render-on-open]] and [[long-lived-render-on-close]]. The keys in this map should be considered an implementation detail
  except for :hifi.datastar/<render, a promesa channel that when written to will cause a re-render just for this connection,
  and :hifi.datastar/<cancel, a promesa channel that when written to will close the <render channel and stop the render loop."
  [req render-fn & {:as   _opts
                    :keys [view-hash-fn error-report-fn patch-elements-opts multicaster]
                    :or   {view-hash-fn        codec/digest
                           error-report-fn     #(tap> ["Error in long-lived render" %1 %2])
                           ;; error-report-fn     #(log/error %1 "Error in long-lived render" %2)
                           patch-elements-opts {d*/use-view-transition false}}}]
  (let [refresh-mult (or (spec/refresh-mult multicaster) (-> req spec/multicaster spec/refresh-mult))
        ;; Dropping buffer is used here as we don't want a slow handler
        ;; blocking other handlers. Mult distributes each event to all
        ;; taps in parallel and synchronously, i.e. each tap must
        ;; accept before the next item is distributed.
        <render      (sp/tap! refresh-mult (sp/chan :buf (sp/dropping-buffer 1)))
        ;; Ensures at least one render on connect
        _            (sp/put! <render [:first-render])
        ;; poison pill for work cancelling
        <cancel      (sp/chan)]
    {spec/<cancel             <cancel
     spec/<render             <render
     spec/render-fn           render-fn
     spec/view-hash-fn        view-hash-fn
     spec/error-report-fn     error-report-fn
     spec/patch-elements-opts patch-elements-opts}))

(defn update-req [req event]
  (-> req
      (assoc spec/first-render? (= (first event) :first-render))
      (assoc :hifi.datastar/tab-state (tab-state/tab-state! req))))

(defn long-lived-render-on-open
  "Starts an async loop to render on changes.

  Should be called from the on-open callback of the sse connection handler.

  Arguments:
    - `opts` - a context map created by [[long-lived-render-setup]]
    - `sse-gen` - the sse generator from the d* sdk

  The render function will be called with the original request map with ::first-render? set to true on the first render."
  #_{:clj-kondo/ignore [:unused-binding]}
  [{:as opts ::keys [<render <cancel render-fn view-hash-fn
                     error-report-fn patch-elements-opts]} req sse-gen]
  (assert <render)
  (assert <cancel)
  (sp/go-loop [last-view-hash (get-in req [:headers "last-event-id"])]
    (let [[event ch] (sp/alts! [<cancel <render]
                               :priority true ;; we want work cancelling to have higher priority
                               )]
      (condp = ch
        <cancel (do
                  (sp/close! <render)
                  (sp/close! <cancel))
        <render (let [req           (update-req req event)
                      new-view      ^String (try-log error-report-fn req (render-fn req))
                      new-view-hash ^String (view-hash-fn new-view)]
                  ;; only send an event if the view has changed
                  (when (and new-view (not= last-view-hash new-view-hash))
                    (d*/patch-elements! sse-gen new-view (assoc patch-elements-opts d*/id new-view-hash)))
                  (recur new-view-hash))))))

(defn long-lived-render-on-close [{::keys [<cancel]}]
  (sp/put <cancel :cancel))

(comment
  ;; sample usage
  {:get {:handler (fn [req]
                    (let [ctx (long-lived-render-setup req render-fn)]
                      (hk-gen/->sse-response req
                                             {:headers {}}
                                             hk-gen/on-open (fn [sse-gen]
                                                              (long-lived-render-on-open ctx sse-gen))
                                             hk-gen/on-close (fn [_ _]
                                                               (long-lived-render-on-close ctx))
                                             d*com/write-profile (brotli/->brotli-profile))))}})

(def !datastar-asset (assets/static-asset false
                                          {:resource-path "hifi-datastar/datastar@1.0.0-RC.5.js"
                                           :content-type  "text/javascript"
                                           :compress-fn   (fn [body]
                                                            (brotli/compress body :quality 11))
                                           :encoding      "br"
                                           :route-path    "/datastar.js"}))
(defn rerender-all!
  "Rerenders all connected SSE clients.

  - event - some data passed to the render handlers, conventionally it is a vector with the first
            element being a keyword."
  ([]
   (rerender-all! nil))
  ([event]
   (mult/rerender-all! event)))
