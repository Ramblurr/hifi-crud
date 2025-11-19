(ns hifi.repl.spec)

(def NREPLServerOptions
  ;; TODO improve docs
  [:map {:name ::nrepl-server}
   [:create-nrepl-port-file? {:doc "Whether to create the .nrepl-port file in the cwd" :default true} :boolean]
   [:port {:doc "Which port to use" :default 0} int?]
   [:bind {:doc "Which IP to bind" :default "127.0.0.1"} :string]])
