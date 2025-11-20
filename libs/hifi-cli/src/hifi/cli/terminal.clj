(ns hifi.cli.terminal
  (:require
   [bling.core :as bling]
   [clj-commons.format.exceptions :as pretty]
   [hifi.util.terminal :as t]
   [zprint.core :as z]
   ;; [clj-commons.ansi :as ansi]
   ))

(set! *warn-on-reflection* true)

(def ^:dynamic *debug* false)

(defn pprint [coll & _rest]
  (let [opts {:style (t/zprint-style)}]
    (if (t/color?)
      (z/czprint coll opts)
      (z/zprint coll opts))))

(defn print-ex
  ([e] (clojure.core/println (pretty/format-exception e))))

(defn print-hifi-error [e]
  (let [msg (ex-message e)
        {::keys [desc suggest docurl]} (ex-data e)]
    (bling/print-bling [:bold.error "Error: "] msg)
    (println)
    (when suggest
      (bling/print-bling suggest)
      (println))
    (when desc
      (bling/print-bling desc)
      (println))
    (when docurl
      (bling/print-bling "View more information at" [{:href docurl} docurl])
      (println))
    (when *debug*
      (println (pretty/format-exception e {:properties false})))))

(defn msg
  "Print informational message to stdout"
  [& args]
  (apply bling/print-bling args))

(defn error
  ([msg]
   (error msg {} nil))
  ([msg map]
   (error msg map nil))
  ([msg map cause]
   (ex-info msg (merge {::error true} map) cause)))

(defn print-error [e]
  (tap> [:GOT e])
  (if (::error (ex-data e))
    (print-hifi-error e)
    (print-ex e)))

(defn warn
  "Print warning message to stderr"
  [& args]
  (apply bling/print-bling [:bold.yellow "Warning: "] args))
