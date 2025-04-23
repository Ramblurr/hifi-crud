(ns app.ui.icon
  (:require
   [malli.experimental.lite :as l]
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

(def Icon
  (with-meta
    {:ico/name (l/optional :keyword)}
    {:name :app.ui/icon}))

(defmethod c/resolve-alias :app.ui/icon
  [_ {:ico/keys [name]
      :as       attrs} _children]
  (uic/validate-opts! Icon attrs)
  (cc/compile
   (update-in (ico name) [1]
              uic/merge-attrs* attrs)))

(ico :cloud-thin)
(ico :phosphor/cloud-thin)
