;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns todomvc.app
  "The classic TodoMVC but server backed."
  (:require
   [cuerdas.core :as str]
   [hifi.datastar :as datastar]
   [hifi.datastar.brotli :as br]
   [hifi.datastar.http-kit :as d*http-kit]
   [hifi.env :as env]
   [hifi.html :as html]
   [hifi.system :as hifi]
   [hifi.system.middleware :as hifi.mw]
   [hifi.util.assets :as assets]
   [hifi.util.shutdown :as shutdown]
   [taoensso.telemere :as t]
   [todomvc.commands :as commands]
   [todomvc.views :as views]))

(def request-rate-limiter
  ;; Basic in-memory rate limiter to prevent demo overloading
  (t/rate-limiter
   {"2 req per 1s"  [2       1000]
    "60 req per 1m" [100 (* 60000)]
    "350 req pr 5m" [350 (* 5 60000)]}))

(def static-asset (partial assets/static-asset (env/dev?)))
(def !base-css (static-asset {:resource-path "base.css" :content-type "text/css"}))
(def !index-css (static-asset {:resource-path "index.css" :content-type "text/css"}))
(def !extra-css (static-asset {:resource-path "extra.css" :content-type "text/css"}))
(def !datastar datastar/!datastar-asset)

(defn init-tab-state
  [{:keys [app/todo-items] :as current} reset?]
  (if (or reset? (nil? todo-items))
    {:app/todo-items  []
     :app/item-filter :filter/all}
    current))

(defn home-view [{:keys [::datastar/tab-state] :as _req}]
  ;; TODO the problem here is that tab-state is out of date, it is fixed in time at the beginning of the long lived request
  (html/->str
   (list
    (html/stylesheet {:!asset !extra-css :id "css-extra"})
    [:main#morph {:data-signals__ifmissing (datastar/edn->json {:choice ""}) :style "font-family: sans;"}
     (views/app-view tab-state)])))

(defn forwarded-remote-addr-request
  "Change the :remote-addr key of the request map to the FIRST value present in
  the X-Forwarded-For header. See: wrap-forwarded-remote-addr."
  [request]
  (if-let [forwarded-for (get-in request [:headers "x-forwarded-for"])]
    (let [remote-addr (str/trim (re-find #"^[^,]*" forwarded-for))]
      (assoc request :remote-addr remote-addr))
    request))

(defn wrap-correct-forwarded-remote-addr
  "Middleware that changes the :remote-addr of the request map to the
  FIRST value present in the X-Forwarded-For header."
  [handler]
  (fn
    ([request]
     (handler (forwarded-remote-addr-request request)))
    ([request respond raise]
     (handler (forwarded-remote-addr-request request) respond raise))))

(defn wrap-retrieve-original-remote-address [f]
  (fn [request]
    (let [[_ real-remote-address] (re-matches #".*<->.*/(.*):\d+$" (str (:async-channel request)))]
      (f (assoc request :remote-addr real-remote-address)))))

(defn wrap-rate-limit [handler]
  (fn [req]
    (let [rate-limited (request-rate-limiter (get-in req [:headers "remote-addr"]))]
      (if rate-limited
        {:status  429
         :headers {"Content-Type" "text/plain"}
         :body    (str "rate limited: " (first rate-limited))}
        (handler req)))))

(defn routes []
  ;; We use the default hifi middleware chain for hypermedia applications
  [""  {:middleware hifi.mw/hypermedia-chain}
   [""  {:middleware [wrap-retrieve-original-remote-address
                      wrap-correct-forwarded-remote-addr
                      wrap-rate-limit]}
    ;; Render the page shim, and then render the real view over the SSE connection
    ["/" {:get  {:handler (html/shim-handler
                           (html/shim-page-resp
                            {:body        (html/shim-document {:title          "Hello World Datastar"
                                                               :csrf-cookie-js (when (env/dev?) html/csrf-cookie-js-dev)
                                                               :head           (list (html/script {:defer true :type "module" :!asset !datastar})
                                                                                     (html/stylesheet {:!asset !base-css})
                                                                                     (html/stylesheet {:!asset !index-css})
                                                                                     (html/stylesheet {:!asset !extra-css :id "css-extra"}))
                                                               :body-pre       [:div]})
                             :compress-fn #(br/compress % :quality 11)
                             :encoding    "br"}))}
          :post {:handler (d*http-kit/render-handler #'home-view {:init-tab-state-fn #(init-tab-state %2 false)
                                                                  ;; :opts              {:d*.fragments/use-view-transition true}
                                                                  })}}]

    commands/commands]
   ;; Static asset handlers
   (assets/asset->route !base-css)
   (assets/asset->route !index-css)
   (assets/asset->route !extra-css)
   (assets/asset->route !datastar)

   ;; An example of error handling
   ["/error" {:handler (fn [_]
                         (throw (ex-info "Uhoh" {:foo :bar})))}]])

(defonce ^:dynamic *system* nil)

(defn stop []
  (println "Shutting down cleanly")
  (when *system* (hifi/stop *system*)))

(defn -main [& _]
  (println "Starting todomvc on port 3000")
  (shutdown/add-shutdown-hook! ::stop stop)
  (hifi/start {:routes              #'routes
               :port                3000
               :debug-errors?       true
               :reload-per-request? true}))

(datastar/rerender-all!)

(defn start []
  (alter-var-root #'*system* -main))

(comment
  (set! *warn-on-reflection* true)
  (do
    (require '[portal.api :as p])
    (p/open {:theme :portal.colors/gruvbox})
    (add-tap #'p/submit))

  (do
    (stop)
    (start))
  ;; rcf

  *system*
  (-> *system* :donut.system/instances :hifi/components :tab-state :!tab-state-store deref)

  ;;
  )
