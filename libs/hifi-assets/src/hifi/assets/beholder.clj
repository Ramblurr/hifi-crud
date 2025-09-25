;; Copyright © 2021 Nextjournal
;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EPL-1.0
;;
;; A vendored version of https://github.com/nextjournal/beholder
;; that supports more options
(ns ^:no-doc hifi.assets.beholder
  (:import
   [io.methvin.watcher
    DirectoryChangeEvent
    DirectoryChangeEvent$EventType
    DirectoryChangeListener
    DirectoryWatcher]
   [io.methvin.watcher.hashing FileHasher]
   [io.methvin.watcher.visitor FileTreeVisitor]
   [java.nio.file Paths]))

(set! *warn-on-reflection* true)

(defn- fn->listener ^DirectoryChangeListener [f]
  (reify
    DirectoryChangeListener
    (onEvent [_this e]
      (let [path (.path ^DirectoryChangeEvent e)]
        (condp = (. ^DirectoryChangeEvent e eventType)
          DirectoryChangeEvent$EventType/CREATE   (f {:type :create :path path})
          DirectoryChangeEvent$EventType/MODIFY   (f {:type :modify :path path})
          DirectoryChangeEvent$EventType/DELETE   (f {:type :delete :path path})
          DirectoryChangeEvent$EventType/OVERFLOW (f {:type :overflow :path path}))))))

(defn- to-path [& args]
  (Paths/get ^String (first args) (into-array String (rest args))))

(defn create
  "Creates a watcher taking a callback function `cb` that will be invoked
  whenever a file in one of the `paths` chages.

  Not meant to be called directly but use `watch`"
  ^DirectoryWatcher  [cb paths {:keys [file-hasher ^FileTreeVisitor visitor]}]
  (let [^FileTreeVisitor file-hasher
        (case file-hasher
          (nil true :last-modified) FileHasher/LAST_MODIFIED_TIME
          :slow                     FileHasher/DEFAULT_FILE_HASHER
          false                     nil)]
    (-> (cond-> (DirectoryWatcher/builder)
          visitor
          (.fileTreeVisitor visitor)
          file-hasher
          (.fileHasher file-hasher)
          (false? file-hasher)
          (.fileHashing false))
        (.paths (map to-path paths))
        (.listener (fn->listener cb))
        (.build))))

(defn watch
  "Creates a directory watcher that will invoke the callback function `cb` whenever
  a file event in one of the `paths` occurs. Watching will happen asynchronously.

  Returns a directory watcher that can be passed to `stop` to stop the watch."
  [cb paths opts]
  (assert (sequential? paths))
  (doto (create cb paths opts)
    (.watchAsync)))

(defn stop
  "Stops the watch for a given `watcher`."
  [^DirectoryWatcher watcher]
  (.close watcher))

(comment
  ;; to start a watch with a callback function and paths to watch
  (def watcher
    (watch prn ["src"] {}))

  ;; stop the watch again using the return value from watch
  (stop watcher))
