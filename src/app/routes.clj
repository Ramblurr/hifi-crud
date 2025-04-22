(ns app.routes
  (:require
   [app.tab-state :as tab-state]
   [app.effects :as effects]
   [app.home :as home]
   [app.auth :as auth]
   [engine.shell :as shell]
   [hyperlith.core :as h]))

(defn make-url-for [pages]
  (fn url-for [path]
    (if (contains? pages path)
      (get-in pages [path :path])
      (do
        (tap> (ex-info "Path not found" {:path path}))
        "#"))))

(def pages
  (merge home/pages
         auth/pages))

(def url-for (make-url-for pages))

(defn load-css []
  (h/static-asset
   {:body         (h/load-resource "public/compiled.css")
    :content-type "text/css"
    :compress?    false}))

(def static-css (load-css))

(defn css-thunk []
  (if (h/env :dev?)
    {:path    "/app.css"
     :handler #(((load-css) :handler) %)}
    static-css))

(def default-shim-handler
  (h/shim-handler
   {:head
    (h/html
     [:link#css {:rel "stylesheet" :type "text/css" :href (:path (css-thunk))}])
    :body-post
    (h/html
     [:svg {:style "display: none"}
      [:symbol {:id "svg-sprite-spinner" :fill "none", :viewbox "0 0 24 24"}
       [:circle {:class "opacity-25", :cx "12", :cy "12", :r "10", :stroke "currentColor", :stroke-width "4"}]
       [:path {:class "opacity-75", :fill "currentColor", :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]])}))

(defn action-dispatch-command [{:keys [sid engine body query-params] :as _req}]
  (let [command-name (keyword (query-params "cmd"))
        ;; HACK(hyperlith) this is a workaround for the fact that hyperlith doesn't let us return signals async
        !http-return (atom nil)]
    (if (shell/command engine command-name)
      (try
        (shell/dispatch-sync (assoc engine :!http-return !http-return)
                             {:command/kind command-name
                              :sid          sid
                              :signals      body
                              :url-for      url-for}
                             {:interceptors (conj shell/default-global-interceptors :app/cloak-signals)})
        (when-let [http-return @!http-return]
          ;; (tap> [:command-signals-result http-return])
          http-return)
        (catch Throwable t
          (tap> [:command-error t])
          (println t)
          nil))
      (throw (ex-info "Command not found" {:command command-name})))))

(defn pages->routes [pages]
  (into {}
        (for [[_ {:keys [path handler]}] pages]
          {[:get path]  default-shim-handler
           [:post path] (h/render-handler  (fn [req]
                                             (-> req
                                                 (effects/enrich-render)
                                                 (assoc :url-for url-for)
                                                 (assoc :app/tab-state (tab-state/tab-state! (:app/tab-id req)))
                                                 (handler)))
                                           :wrap-req (fn [<ch req]
                                                       (assoc req
                                                              :<ch <ch
                                                              :app/tab-id (tab-state/generate-tab-id)))
                                           :on-open
                                           (fn [{<ch :<ch tab-id :app/tab-id :as req}]
                                             (tab-state/init-tab-state! <ch tab-id))
                                           :on-close
                                           (fn [req]
                                             (tab-state/remove-tab-state! (:app/tab-id req))))})))

(def router
  (h/router
   (merge {[:get ((css-thunk) :path)] ((css-thunk) :handler)
           [:post "/cmd"]             (h/action-handler #'action-dispatch-command)}
          (pages->routes pages))))
