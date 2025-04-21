(ns app.ui.icon
  (:require
   [app.ui.core :as uic]
   [clojure.java.io :as io]
   [dev.onionpancakes.chassis.compiler :as cc]
   [dev.onionpancakes.chassis.core :as c]))

(def phosphor
  {"regular" {:path "icons/phosphor-regular.edn"}
   "bold"    {:path "icons/phosphor-bold.edn"}
   "duotone" {:path "icons/phosphor-duotone.edn"}
   "fill"    {:path "icons/phosphor-fill.edn"}
   "light"   {:path "icons/phosphor-light.edn"}
   "thin"    {:path "icons/phosphor-thin.edn"}})

(defn read-set [v]
  (-> v io/resource slurp read-string))

(def iconsets
  {"phosphor" (into {}
                    (map (comp read-set :path))
                    (vals phosphor))})

(def ^:dynamic *default-iconset* "phosphor")

(defn ico
  ([ico-name]
   (if-let [iconset (namespace ico-name)]
     (get-in iconsets [iconset (keyword (name ico-name))])
     (get-in iconsets [*default-iconset* (keyword ico-name)]))))

(defmethod c/resolve-alias :app.ui/icon
  [_ {:ico/keys [icon]
      :as       attrs} _children]
  (cc/compile
   (update-in (ico icon) [1]
              uic/merge-attrs attrs)))

(ico :cloud-thin)
(ico :phosphor/cloud-thin)
