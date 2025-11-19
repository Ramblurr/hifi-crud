(ns hifi.repl.spec)

(def NREPLServerOptions
  ;; TODO improve docs
  [:map {:name ::nrepl-server}
   [:create-nrepl-port-file? {:doc "Whether to create the .nrepl-port file in the cwd" :default false} :boolean]
   [:cider? {:doc "Whether to add the cider nrepl middleware" :default false} :boolean]
   [:middleware {:doc "Extra nrepl middleware to mix into the default stack" :default []} [:vector :any]]
   [:suppress-start-msg? {:doc "Suppress the start msg printed to stdout" :default false} :boolean]
   [:port {:doc "Which port to use" :default 7000} int?]
   [:bind {:doc "Which IP to bind" :default "127.0.0.1"} :string]])
