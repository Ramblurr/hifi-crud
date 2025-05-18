(ns hifi.datastar
  {:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
  (:import [java.io StringWriter])
  (:require
   [charred.api :as charred]
   [promesa.exec.csp :as sp]
   [hifi.util.assets :as assets]
   [hifi.util.codec :as codec]
   [hifi.datastar.brotli :as br]
   [hifi.datastar.util :as util]
   [starfederation.datastar.clojure.adapter.common :as d*com]
   [starfederation.datastar.clojure.api :as d*]))

(def default-read-json (charred/parse-json-fn {:async? false :bufsize 1024 :key-fn keyword}))
(def default-write-json (charred/write-json-fn {}))

(defn write-json-str
  "Write json to a string.  See options for [[write-json]]."
  [data & {:as args}]
  (let [w (StringWriter.)]
    (default-write-json w data)
    (.toString w)))

(def edn->json write-json-str)

(defn brotli-write-profile
  "Constructs a write-profile for compressing D* SSE respsonses with brotli.

  `opts` are passed to [[hifi.datastar.brotli/->brotli-os]]."

  ([]
   (brotli-write-profile nil))
  ([opts]
   {d*com/wrap-output-stream (fn [os] (-> os (br/->brotli-os opts) d*com/->os-writer))
    d*com/content-encoding   "br"
    d*com/write!             (d*com/->write-with-temp-buffer!)}))

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
  - merge-framgment-opts - a map of options to pass to the d* sdks merge-fragment see [[starfederation.datastar.clojure.api/merge-fragment!]]
  - multiplexer - the value returned by [[start-render-multiplexer]] (if nil, ::multiplexer is expected in the `req`)

  Returns a map to pass to [[long-lived-render-on-open]] and [[long-lived-render-on-close]]. The keys in this map should be considered an implementation detail
  except for ::<render, a promesa channel that when written to will cause a re-render just for this connection,
  and ::<cancel, a promesa channel that when written to will close the <render channel and stop the render loop."
  [req render-fn & {:as   _opts
                    :keys [view-hash-fn error-report-fn merge-fragment-opts multiplexer]
                    :or   {view-hash-fn        codec/digest
                           error-report-fn     prn
                           merge-fragment-opts {d*/use-view-transition false}}}]
  (let [refresh-mult (or (::refresh-mult multiplexer) (-> req ::multiplexer ::refresh-mult))
        ;; Dropping buffer is used here as we don't want a slow handler
        ;; blocking other handlers. Mult distributes each event to all
        ;; taps in parallel and synchronously, i.e. each tap must
        ;; accept before the next item is distributed.
        <render      (sp/tap! refresh-mult (sp/chan :buf (sp/dropping-buffer 1)))
        ;; Ensures at least one render on connect
        _            (sp/put! <render [:first-render])
        ;; poison pill for work cancelling
        <cancel      (sp/chan)]
    {::<cancel             <cancel
     ::<render             <render
     ::render-fn           render-fn
     ::view-hash-fn        view-hash-fn
     ::req                 req
     ::error-report-fn     error-report-fn
     ::merge-fragment-opts merge-fragment-opts}))

(defn long-lived-render-on-open
  "Starts an async loop to render on changes.

  Should be called from the on-open callback of the sse connection handler.

  Arguments:
    - `opts` - a context map created by [[long-lived-render-setup]]
    - `sse-gen` - the sse generator from the d* sdk

  The render function will be called with the original request map with ::first-render? set to true on the first render."
  #_{:clj-kondo/ignore [:unused-binding]}
  [{:as opts ::keys [req <render <cancel render-fn view-hash-fn
                     error-report-fn merge-fragment-opts]} sse-gen]
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
        <render (let [req           (assoc req ::first-render? (= (first event) :first-render))
                      new-view      ^String (try-log error-report-fn req (render-fn req))
                      new-view-hash ^String (view-hash-fn new-view)]
                  ;; only send an event if the view has changed
                  (when (and new-view (not= last-view-hash new-view-hash))
                    (d*/merge-fragment! sse-gen new-view (assoc merge-fragment-opts d*/id new-view-hash)))
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
                                             d*com/write-profile (brotli-write-profile))))}})

(defonce ^:private !refresh-ch (atom nil))

(defn rerender-all!
  "Rerenders all connected SSE clients.

  - event - some data passed to the render handlers, conventionally it is a vector with the first
            element being a keyword.
  "
  ([]
   (rerender-all! nil))
  ([event]
   (when-let [<refresh-ch @!refresh-ch]
     (sp/put <refresh-ch (or event [])))))

(defn throttle [<in-ch msec]
  (let [;; No buffer on the out-ch as the in-ch should be buffered
        <out-ch (sp/chan)]
    (sp/go
      (util/while-some [event (sp/take! <in-ch)]
                       (sp/put! <out-ch event)
                       (Thread/sleep ^long msec)))
    <out-ch))

(defn start-render-multiplexer [{:keys [max-refresh-ms on-refresh]
                                 :or   {max-refresh-ms 100}}]
  (let [<refresh-ch  (sp/chan :buf (sp/dropping-buffer 1))
        _            (reset! !refresh-ch <refresh-ch)
        refresh-mult (-> (throttle <refresh-ch max-refresh-ms)
                         (sp/pipe
                          (sp/chan :buf (sp/dropping-buffer 1)
                                   :xf
                                   (map (fn [event]
                                          (when (and on-refresh (seq event))
                                            (on-refresh event))
                                          event))))
                         sp/mult*)]
    {::<refresh-ch  <refresh-ch
     ::refresh-mult refresh-mult}))

(comment
  (def _s (start-render-multiplexer {:max-refresh-ms 100}))
  (stop-render-multiplexer _s)
  ;; rcf
  ;;
  )

(defn stop-render-multiplexer [{::keys [<refresh-ch refresh-mult]}]
  (when <refresh-ch
    (sp/close! <refresh-ch)
    (reset! !refresh-ch nil))
  (when refresh-mult
    (sp/close! refresh-mult)))

(def !datastar-asset (assets/static-asset false
                                          {:resource-path "hifi-datastar/datastar@rc9.js"
                                           :content-type  "text/javascript"
                                           :compress-fn   (fn [body]
                                                            (br/compress body :quality 11))
                                           :encoding      "br"
                                           :route-path    "/datastar.js"}))

(def DatastarRenderMultiplexerOptions
  [:map {:name ::render-multiplexer}
   [:max-refresh-ms {:default 100
                     :doc     "Don't re-render clients more often than this many milliseconds"} :int]
   [:on-refresh {:optional true
                 :doc      "An arity/1 function called with the refresh event"} fn?]])

(def DatastarRenderMultiplexerComponent
  "A donut.system component for the datastar render multiplexer
  Config:
    - :hifi/options - See DatastarRenderMultiplexerOptions"
  {:donut.system/start  (fn  [{{:keys [:hifi/options]} :donut.system/config}]
                          ;; workaround https://github.com/donut-party/system/issues/43
                          (let [multiplexer (start-render-multiplexer options)]
                            (delay multiplexer)))
   :donut.system/stop   (fn [{instance :donut.system/instance}]
                          (stop-render-multiplexer @instance))
   :donut.system/config {}
   :hifi/options-schema DatastarRenderMultiplexerOptions
   :hifi/options-ref    [:hifi/components :options :datastar-render-multiplexer]})
