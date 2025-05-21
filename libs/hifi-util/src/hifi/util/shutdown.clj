(ns hifi.util.shutdown
  "Graceful ordered shutdown hook management

  JVM Shutdown hooks are useful for cleaning up resources, saving state, etc.

  However once a JVM shutdown hook has been registered, it cannot be removed, which
  rubs against the grain of REPL driven development.

  This namespace provides utilities for managing application shutdown hooks,
  enabling graceful termination of services. It allows registering functions
  that will be automatically executed in a defined order when the JVM shuts
  down.

  Hooks are executed in the order that they were added.

  ### Public API:

  - `add-shutdown-hook!` - Registers a function to be executed during JVM shutdown
  - `remove-shutdown-hook!` - Removes a previously registered shutdown hook
  - `list-hooks` - Returns all currently registered shutdown hook")

(defonce ^:private !hooks (atom {}))

(defn- execute-shutdown-hooks! []
  (doseq [{:keys [hook key]} (->> @!hooks vals (sort-by :order))]
    (when hook
      (try
        (hook)
        (catch Throwable t
          (println t "Error executing shutdown hook " key))))))

(def ^:private jvm-register-promise (promise))

(defn- register-system-hook! []
  (when-not (realized? jvm-register-promise)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable execute-shutdown-hooks!))
    (deliver jvm-register-promise true)))

;; --------------------------------------------------------------------------------------------
;; Public API

(defn add-shutdown-hook!
  "Adds a shutdown hook with a key and a function to be executed on shutdown."
  [key f]
  (assert (keyword? key) "Key must be a keyword")
  (assert (fn? f) "Function must be a function")
  (swap! !hooks (fn [hooks]
                  (assoc hooks key {:hook  f
                                    :key   key
                                    :order (or (:order (get hooks key))
                                               (count hooks))})))
  (register-system-hook!)
  nil)

(defn remove-shutdown-hook!
  "Removes a shutdown hook by its key."
  [key]
  (assert (keyword? key) "Key must be a keyword")
  (swap! !hooks dissoc key))

(defn list-hooks
  "Returns the current shutdown hooks."
  []
  @!hooks)

(comment
  (add-shutdown-hook! :wut #(prn "wut"))
  (add-shutdown-hook! :lol #(prn "lol"))

  (list-hooks)

  (remove-shutdown-hook! :lol)
  (remove-shutdown-hook! :wut)

  (list-hooks)

  (doseq [k ["baz" "bar" "foo" "quux" "quuux" "quuuux" "quuuuxx" "wib" "wom" "zaz" "ziz" "zox" "xox" "lol" "wut"]]
    #_(remove-shutdown-hook! (keyword k))
    (add-shutdown-hook! (keyword k) #(prn k)))

  (execute-shutdown-hooks!))
