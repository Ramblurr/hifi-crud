(ns hifi.repl
  "TODO docstring"
  (:require
   [hifi.repl.spec :as spec]
   [nrepl.cmdline]
   [nrepl.server :as nrepl]
   [donut.system :as ds]
   [hifi.core :as h]))

(def ^:private hifi-only-opts [:create-nrepl-port-file?])

(h/defcomponent NREPLServerComponent
  "TODO docstring"
  {::ds/start        (fn [{:keys [:donut.system/config]}]
                       (try
                         (let [server (nrepl/start-server (apply dissoc config hifi-only-opts))]
                           (when (:create-nrepl-port-file? config)
                             (nrepl.cmdline/save-port-file server {}))
                           (println (str "nREPL server started on " (:bind config) ":" (:port server)))
                           (assoc config ::server server))
                         (catch Exception e
                           ;; TODO log error
                           ;; (log/error "failed to start the nREPL server on port:" port)
                           (throw e))))
   ::ds/stop         (fn [{::ds/keys [instance]}]
                       (nrepl/stop-server (::server instance)))
   :hifi/config-spec spec/NREPLServerOptions
   :hifi/config-key  :hifi.repl/nrepl
   ::ds/config       {}})

(h/defplugin plugin
  "TODO docstring"
  {:hifi/repl {:hifi.repl/repl NREPLServerComponent}})
