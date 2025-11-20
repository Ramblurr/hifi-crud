(ns hifi.util.terminal
  (:require
   [clojure.string :as str]
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(defn color-theme
  "Determines the color theme used by hifi. One of :light or :dark. Defaults to :dark"
  []
  (let [theme (some-> (System/getenv "HIFI_THEME")
                      str/lower-case
                      str/trim)]
    (case theme
      "light" :light
      "dark" :dark
      :dark)))

(defn dark? []
  (= :dark (color-theme)))

(defn color? []
  (str/blank? (System/getenv "NO_COLOR")))

(defn zprint-style []
  (if (and (color?) (dark?))
    [:dark-color-map :community]
    [:community]))

(def ^:const show-cursor "\u001B[?25h")
(def ^:const hide-cursor "\u001B[?25l")
(def ^:const erase-line  "\u001B[2K")

(def spinners (edn/read-string (slurp (io/resource "hifi/util/terminal/spinners.edn"))))
(def port (java.util.concurrent.SynchronousQueue.))

(defn done []
  (.put port :done)
  (.take port))

(defn ok []
  (.write *err* "✓")
  (.flush *err*)
  (.put port :ok))

(defn blit [frame msg]
  (.write *err* erase-line)
  (.write *err* "\r")
  (.write *err* frame)
  (when msg
    (.write *err* " ")
    (.write *err* msg))
  (.flush *err*))

(defn spin
  "The spin function loops a spinner to STDOUT until a call to (done)"
  [& {:keys [type ms disappear? msg]
      :or   {type :dots, disappear? false}}]
  (let [{:keys [interval frames]} (get spinners type)
        frames   (vec frames)
        length   (count frames)
        delay-ms (long (or ms interval 100))]
    (.write *err* hide-cursor)
    (.flush *err*)
    (.start
     (Thread/ofVirtual)
     ^Runnable
     (fn []
       (loop [i 0]
         (let [frame   (nth frames i)
               i'      (mod (inc i) length)
               msg-sig (.poll port delay-ms java.util.concurrent.TimeUnit/MILLISECONDS)]
           (if (= msg-sig :done)
             (do
               (if disappear?
                 (do
                   (.write *err* erase-line)
                   (.write *err* "\r")
                   (.flush *err*)
                   ;; acknowledge to `done` without .write printing ✓
                   (.put port :ok))
                 (do
                   (.write *err* erase-line)
                   (.write *err* "\r")
                   ;; ok prints ✓ and also acknowledges
                   (ok)
                   (when msg
                     (.write *err* " ")
                     (.write *err* msg))
                   (.flush *err*)))
               (.write *err* show-cursor)
               (.flush *err*))
             (do
               (blit frame msg)
               (recur i')))))))))
(defmacro with-spinner
  [opts & body]
  `(let [opts# ~opts]
     (apply spin (mapcat identity opts#))
     (try
       ~@body
       (finally
         (done)))))
