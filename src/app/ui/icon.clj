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

(def customer
  [:svg {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 67.733 67.733" :fill "currentColor"}
   [:path {:fill "none" :d "M0 0h67.733v67.733H0Z"}]
   [:g [:path {:d "M55.498 24.364a2.116 2.116 0 0 0-2.115 2.117v2.063a2.116 2.116 0 0 0 2.115 2.117 2.116 2.116 0 0 0 2.117-2.117V26.48a2.116 2.116 0 0 0-2.117-2.117zM55.498 43.354a2.116 2.116 0 0 0-2.115 2.117v2.063a2.116 2.116 0 0 0 2.115 2.115 2.116 2.116 0 0 0 2.117-2.115v-2.063a2.116 2.116 0 0 0-2.117-2.117z"}]
    [:path {:d "M54.123 28.014c-1.675.001-3.2.706-4.16 1.788-.96 1.08-1.393 2.44-1.393 3.77 0 1.329.434 2.688 1.395 3.769.96 1.08 2.484 1.783 4.158 1.783h3.437c.616 0 .81.156.995.365.185.209.326.57.326.959 0 .39-.14.75-.324.957-.185.208-.379.362-.995.361a2.117 2.117 0 0 0-.002 0h-4.125c-.754 0-1.32-.565-1.32-1.32a2.116 2.116 0 0 0-2.117-2.117 2.116 2.116 0 0 0-2.117 2.117c0 3.043 2.512 5.553 5.554 5.553h4.125c1.675 0 3.198-.703 4.159-1.783.96-1.081 1.394-2.438 1.394-3.768 0-1.33-.432-2.688-1.392-3.77-.369-.414-.879-.71-1.39-.996a2.116 2.116 0 0 0 2.097-2.113c0-3.042-2.513-5.555-5.555-5.555zm0 4.235h2.75c.755 0 1.322.565 1.322 1.32a2.116 2.116 0 0 0 1.037 1.67c-.539-.164-1.074-.347-1.67-.348a2.117 2.117 0 0 0-.002 0h-3.437c-.616 0-.812-.155-.996-.363-.185-.208-.324-.568-.324-.957 0-.39.14-.75.326-.959.185-.208.38-.362.994-.363z"}]]
   [:path {:d "M28.96 10.583c-9.91 0-17.99 8.08-17.99 17.992 0 9.911 8.08 17.992 17.99 17.992 9.912 0 17.993-8.08 17.993-17.992 0-9.911-8.08-17.992-17.992-17.992zm0 4.234c7.624 0 13.76 6.134 13.76 13.758a13.727 13.727 0 0 1-13.76 13.758 13.725 13.725 0 0 1-13.757-13.758 13.725 13.725 0 0 1 13.758-13.758z"}]
   [:path {:d "M28.96 42.333c-9.749 0-18.059 4.456-23.843 11.338a2.116 2.116 0 0 0 .258 2.982 2.116 2.116 0 0 0 2.982-.258c5.09-6.056 12.129-9.828 20.604-9.828s15.515 3.772 20.605 9.828a2.116 2.116 0 0 0 2.983.258 2.116 2.116 0 0 0 .258-2.982C47.022 46.789 38.71 42.333 28.96 42.333z"}]])

(def custom-iconset
  {:customer customer})

(defn ico
  ([ico-name]
   (if-let [iconset (namespace ico-name)]
     (get-in iconsets [iconset (keyword (name ico-name))])
     (or
      (get-in custom-iconset [(keyword ico-name)])
      (get-in iconsets [*default-iconset* (keyword ico-name)])))))

(def doc-icon
  {:examples ["[icon/Icon {::icon/name :cloud-thin}]"
              "[icon/Icon {::icon/name :phosphor/cloud-thin :class \"text-teal-500 size-5\"}]"]
   :ns       *ns*
   :name     'Icon
   :desc     "Renders an icon from the available icon collections"
   :alias    ::icon
   :schema
   [:map {}
    [::name {:doc "The name of the icon to display. Can be namespaced with iconset (e.g. :phosphor/cloud-thin) or just the icon name (e.g. :cloud-thin)"}
     :keyword]]})

(def ^{:doc (uic/generate-docstring doc-icon)} Icon
  ::icon)

(defmethod c/resolve-alias ::icon
  [_ {::keys [name]
      :as    attrs} _children]
  (uic/validate-opts! doc-icon attrs)
  (cc/compile
   (update-in (ico name) [1]
              uic/merge-attrs* attrs)))

(ico :cloud-thin)
(ico :phosphor/cloud-thin)
(def doc-spinner
  {:examples ["[icon/Spinner]"
              "[icon/Spinner {:class \"size-5\"}]"]
   :ns       *ns*
   :name     'Spinner
   :alias    ::spinner
   :desc     "Renders an animated svg spinner"
   :schema
   [:map {}]})

(def ^{:doc (uic/generate-docstring doc-spinner)} Spinner
  ::spinner)

(defmethod c/resolve-alias
  ::spinner
  [_ {:as attrs} _children]
  (cc/compile
   [:svg {:class (uic/merge-attrs attrs :class  "spinner animate-spin")}
    [:use {:href "#svg-sprite-spinner"}]]))

(def doc-logotype
  {:examples ["[icon/Logotype]"
              "[icon/Logotype {:class \"size-5\"}]"]
   :ns       *ns*
   :name     'Logotype
   :alias    ::logotype
   :desc     "Renders the logo with type of the brand"
   :schema   [:map {}]})

(def ^{:doc (uic/generate-docstring doc-logotype)} Logotype ::logotype)

(defmethod c/resolve-alias
  ::logotype
  [_ {:as attrs} _children]
  (cc/compile
   [Icon (merge {::name :rocket
                 :alt   "Your Company"}
                attrs)]))

(def doc-logomark
  {:examples ["[icon/Logomark]"
              "[icon/Logomark {:class \"size-5\"}]"]
   :ns       *ns*
   :name     'Logomark
   :alias    ::logomark
   :desc     "Renders the logo mark of the brand"
   :schema   [:map {}]})

(def ^{:doc (uic/generate-docstring doc-logomark)} Logomark ::logomark)

(defmethod c/resolve-alias
  ::logomark
  [_ {:as attrs} _children]
  (cc/compile
   [Icon (merge {::name :rocket
                 :alt   "Your Company"}
                attrs)]))
