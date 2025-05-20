(ns example
  "This is an example application showing a minimal datastar powered application using CQRS principles."
  (:require
   [hifi.datastar :as datastar]
   [hifi.datastar.brotli :as br]
   [hifi.datastar.http-kit :as d*http-kit]
   [hifi.datastar.tab-state :as tab-state]
   [hifi.env :as env]
   [hifi.html :as html]
   [hifi.system :as hifi]
   [hifi.system.middleware :as hifi.mw]
   [hifi.util.assets :as assets])
  (:import
   [java.time Duration]))

(def static-asset (partial assets/static-asset (env/dev?)))
(def !css (static-asset {:resource-path "demo.css" :content-type "text/css"}))
(def !datastar datastar/!datastar-asset)

(def problem ["The neuro-processor is preconstricted!" "The sub-modulator is congested!" "The holo-feed is interelectrified!" "The electro-boiler is intracomposed! "])
(def inflect-verb {"Reverberate"  "Reverberated"
                   "Polarize"     "Polarized"
                   "Transmogrify" "Transmogrified"
                   "Wiggle"       "Wiggled"
                   "Frag"         "Fraged"})
(def verb (keys inflect-verb))
(def object ["the synaptic charge coil" "the positronic flux conduits" "the dimensional warp cams" "the fundamental resonance ring" "the isostatic injection splitters"])

(defn init-tab-state
  [{:keys [prob] :as current} reset?]
  (if (or reset? (nil? prob))
    (let [prob              (first (shuffle problem))
          [verb1 verb2]     (take 2 (shuffle verb))
          [object1 object2] (take 2 (shuffle object))]
      (merge current
             {:verb1 verb1 :verb2 verb2 :prob prob :object1 object1 :object2 object2}))
    current))

(defn home-view [{:keys [::datastar/tab-state] :as _req}]
  (let [{:keys [action-log verb1 verb2 prob object1 object2]} tab-state]
    (html/->str
     [:main#morph {:data-signals__ifmissing (datastar/edn->json {:choice ""}) :style "font-family: sans;"}
      [:h1 "Hello World w/ Datastar "]
      [:aside {:style "padding: 5px; border: 1px solid #999; max-width: 500px;"}
       "This is a small example of a HiFi Datastar app using several features:"
       [:ul
        [:li "Signals w/ form inputs"]
        [:li "Actions w/ query parameters"]
        [:li "Per-tab state (refresh: new tab state, reconnect: persist the tab-state)"]
        [:li "HiFI static assets"]]]

      [:p "Oh no! " prob]
      [:p "Quick, identify the problematic gizmo:"]
      [:label  {:for "c1"}
       [:input {:id "c1" :type :radio :data-bind "choice" :name "choice" :value object1}]
       object1]
      "   or the   "
      [:label {:for "c2"}
       [:input {:id "c2" :type :radio :data-bind "choice" :name "choice" :value object2}]
       object2]
      [:div {:data-show "!!$choice"}
       [:p "Great. Now, what will fix it?"]
       [:button {:type :button :data-on-click (format "@post('/cmd?cmd=%s')" verb1)} verb1]
       " or "
       [:button {:type :button :data-on-click (format  "@post('/cmd?cmd=%s')" verb2)} verb2]]
      [:section
       (when (seq action-log)
         [:p "Action log:"
          [:ul
           (for [act action-log]
             [:li act])]])]])))

(defn cmd-handler [req]
  (let [cmd              (get-in req [:query-params "cmd"])
        {:keys [choice]} (::datastar/signals req)
        action           (str (inflect-verb cmd "BZZZZ") "ed the " choice)]
    (tab-state/transact! req (fn [state]
                               (-> state
                                   (init-tab-state true)
                                   (update :action-log (fnil conj []) action))))

    nil))

(defn routes []
  ;; We use the default hifi middleware chain for hypermedia applications
  [""  {:middleware hifi.mw/hypermedia-chain}
   ;; Render the page shim, and then render the real view over the SSE connection
   ["/" {:get  {:handler (html/shim-handler
                          (html/shim-page-resp
                           {:body        (html/shim-document {:title          "Hello World Datastar"
                                                              :csrf-cookie-js (when (env/dev?) html/csrf-cookie-js-dev)
                                                              :head           (list (html/script {:defer true :type "module" :!asset !datastar})
                                                                                    (html/stylesheet {:!asset !css}))
                                                              :body-pre       [:div]})
                            :compress-fn #(br/compress % :quality 11)
                            :encoding    "br"}))}
         :post {:handler (d*http-kit/render-handler #'home-view {:init-tab-state-fn #(init-tab-state %2 false)})}}]

   ;; Handler for commands
   ["/cmd" {:post {:handler (d*http-kit/action-handler cmd-handler)}}]

   ;; Static asset handlers
   (assets/asset->route !css)
   (assets/asset->route !datastar)

   ;; An example of error handling
   ["/error" {:handler (fn [_]
                         (throw (ex-info "Uhoh" {:foo :bar})))}]])
(defn -main [& _]
  (hifi/start {:routes              #'routes
               :port                3000
               :debug-errors?       true
               :reload-per-request? true
               :hifi/components     {;; example of overriding builtin hifi component options, see hifi.system.spec for all options
                                     :ring-handler {:default-handler-opts {:not-found (fn [_]
                                                                                        {:status  404
                                                                                         :headers {"content-type" "text/plain"}
                                                                                         :body    "Not found!"})}}
                                     :tab-state    {:clean-job-period    (Duration/ofSeconds 60)
                                                    :clean-age-threshold (Duration/ofSeconds 120)}}}))

(datastar/rerender-all!)

(comment
  (defonce ^:dynamic *system* nil)

  ;; start
  (alter-var-root #'*system* -main)
  ;; stop
  (when *system* (hifi/stop *system*))

  *system*
  ;;
  )
