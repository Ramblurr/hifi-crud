(ns app.main2
  (:require
   [app.auth :as auth]
   [app.home :as home]
   [app.system :as system]
   [hifi.datastar :as datastar]
   [hifi.datastar.brotli :as br]
   [hifi.datastar.http-kit :as d*http-kit]
   [hifi.engine.shell :as shell]
   [hifi.env :as env]
   [hifi.html :as html]
   [hifi.system :as hifi]
   [hifi.system.middleware :as hifi.mw]
   [hifi.util.assets :as assets]
   [starfederation.datastar.clojure.api :as d*]
   [taoensso.telemere :as t]))

(def pages
  (merge home/pages
         auth/pages))

(def forward-to-cmd-keys [:sid ::datastar/tab-state ::datastar/signals ::datastar/tab-id :url-for])

(defn action-dispatch-command [{:app/keys [engine] :keys [sid query-params] :as req}
                               sse-gen]
  (assert sid)
  (assert engine)
  (let [command-name (keyword (query-params "cmd"))]
    (if (shell/command engine command-name)
      (try
        ;; (tap> [:command command-name sid]
        (shell/dispatch-sync (assoc engine ::datastar/sse-gen sse-gen :nonce (:nonce req)  :url-for (:url-for req))
                             (merge
                              (select-keys req forward-to-cmd-keys)
                              {:command/kind command-name})
                             {:interceptors (conj shell/default-global-interceptors :app/cloak-signals)})
        (catch Throwable t
          (tap> [:command-error t])
          (println t)
          nil)
        (finally
          (d*/close-sse! sse-gen)))
      (throw (ex-info "Command not found" {:command command-name})))))

(def static-asset (partial assets/static-asset (env/dev?)))
(def !css (static-asset {:resource-path "public/compiled.css" :route-path "/app.css" :content-type "text/css"}))
(def !datastar datastar/!datastar-asset)
(def !floating-ui-core (static-asset {:resource-path "public/@floating-ui/floating-ui-core@1.6.9.js" :route-path "/@floating-ui/floating-ui-core@1.6.9.js" :content-type "application/javascript"}))
(def !floating-ui-dom (static-asset {:resource-path "public/@floating-ui/floating-ui-dom@1.6.13.js" :route-path "/@floating-ui/floating-ui-dom@1.6.13.js" :content-type "application/javascript"}))
(def !widgets-popover (static-asset {:resource-path "public/widgets/popover.js" :route-path "/widgets/popover.js" :content-type "application/javascript"}))
(def !app-js (static-asset {:resource-path "public/app.js" :route-path "/app.js" :content-type "application/javascript"}))

(def shim-assets (list (html/script {:defer true :type "module" :!asset !datastar})
                       (html/script {:defer true :!asset !floating-ui-core})
                       (html/script {:defer true :!asset !floating-ui-dom})
                       (html/script {:defer true :type "module" :!asset !app-js})
                       (html/stylesheet {:!asset !css})))

(def shim-response (html/shim-page-resp {:body (html/shim-document {:title          "HIFICRUD"
                                                                    :csrf-cookie-js (when (env/dev?) html/csrf-cookie-js-dev)
                                                                    :head           shim-assets
                                                                    :body-post      (html/compile
                                                                                     [:svg {:style "display: none"}
                                                                                      [:symbol {:id "svg-sprite-spinner" :fill "none", :viewbox "0 0 24 24"}
                                                                                       [:circle {:class "opacity-25", :cx "12", :cy "12", :r "10", :stroke "currentColor", :stroke-width "4"}]
                                                                                       [:path {:class "opacity-75", :fill "currentColor", :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]])})

                                         :compress-fn #(br/compress % :quality 11)
                                         :encoding    "br"}))

(def shim-handler (html/shim-handler shim-response))

(defn pages->routes [pages]
  (->> pages
       (mapv (fn [[page-route-name {:keys [path render]}]]
               [path {:name page-route-name
                      :get  shim-handler
                      :post (d*http-kit/render-handler render)}]))

       (into [""])))

(defn routes []
  [""  {:middleware (conj hifi.mw/hypermedia-chain
                          :app)}
   (pages->routes pages)
   ["/cmd" {:post {:handler (d*http-kit/action-handler-async #'action-dispatch-command)}}]
   (assets/asset->route !css)
   (assets/asset->route !datastar)
   (assets/asset->route !floating-ui-core)
   (assets/asset->route !floating-ui-dom)
   (assets/asset->route !widgets-popover)
   (assets/asset->route !app-js)
   ["/error" {:handler (fn [_]
                         (throw (ex-info "Uhoh" {:foo :bar})))}]])

(defonce ^:dynamic *system* nil)

(defn start []
  (let [sys (hifi/start {:routes              #'routes
                         :port                3000
                         :debug-errors?       true
                         :reload-per-request? true}
                        (system/AppSystemDef))]

    (alter-var-root #'*system* (constantly sys))
    (t/log! "hifi-demo started. Visit http://localhost:3000")))

(defn stop []
  (when *system*
    (hifi/stop *system*)
    (t/log! "Stopped")))

(defn -main [& _]
  (routes)
  (do
    (stop)
    (start))
  ;; rcf
  )

(comment

  *system*
  ;; Suck in demo data
  @(d/tx! conn
          (read-string (slurp "extra/data.tx")))

  @(d/tx! conn (take 1000 (read-string (slurp "extra/data.tx"))))
  @(d/tx! conn [{:session/id (str (random-uuid))}])

  @(d/tx! conn [[:db/retractEntity [:session/id "dh6ezrhvsz5t7vcbmRHUAt9GHM8"]]])

  (d/find-all (d/db conn) :invoice/id '[* {:invoice/customer [*]}])

  (d/find-all (d/db conn) :session/id '[*])
  (d/find-all (d/db conn) :user/id '[*])
  (->
   (env/read-env)
   :app/datomic
   :outbox
   :max-execute-time
   type))
