(ns hifi.repl
  "TODO docstring"
  (:require
   [hifi.repl.spec :as spec]
   [nrepl.cmdline]
   [nrepl.server :as nrepl]
   [donut.system :as ds]
   [hifi.core :as h]))

(def ^:private hifi-only-opts [:create-nrepl-port-file? :middleware :cider?])

(defn- load-cider-mw []
  (try
    ((requiring-resolve 'hifi.repl.cider/cider-middleware))
    (catch java.io.FileNotFoundException e
      (throw (ex-info "cider/cider-nrepl is not in the classpath, but :hifi.repl/nrepl's :cider? is enabled" {} e)))))

(defn- nrepl-middleware [{:keys [cider? middleware]}]
  (let [cider-mw (when cider? (load-cider-mw))]
    (concat cider-mw middleware)))

(defn start-nrepl [config]
  (try
    (let [mw (nrepl-middleware config)
          handler (apply  nrepl/default-handler mw)
          server (-> (apply dissoc config hifi-only-opts)
                     (assoc :handler handler)
                     (nrepl/start-server))]
      (when (:create-nrepl-port-file? config)
        (nrepl.cmdline/save-port-file server {}))
      (println (str "nREPL server started on " (:bind config) ":" (:port server)))
      (assoc config ::server server))
    (catch Exception e
      ;; TODO log error
      ;; (log/error "failed to start the nREPL server on port:" port)
      (throw e))))
(defn stop-nrepl [instance]
  (nrepl/stop-server (::server instance)))

(h/defcomponent NREPLServerComponent
  "For running nREPL in production. You do not need this to use nREPL during development. Default options are optmized for production."
  {::ds/start        (fn [{:keys [:donut.system/config]}]
                       (start-nrepl config))
   ::ds/stop         (fn [{::ds/keys [instance]}]
                       (stop-nrepl instance))
   :hifi/config-spec spec/NREPLServerOptions
   :hifi/config-key  :hifi.repl/nrepl
   ::ds/config       {}})

(h/defplugin plugin
  "A hifi plugin for running nREPL in production. You do not need this to use nREPL during development."
  {:hifi/repl {:hifi.repl/repl NREPLServerComponent}})
