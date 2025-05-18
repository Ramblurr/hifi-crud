;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2


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

(def hificrud
  [:svg
   {:xmlns "http://www.w3.org/2000/svg", :viewBox "0 0 164.24 33.655"}
   [:path
    {:fill       "currentColor" :d "M41.935 7.479c-1.94 0-3.704.459-5.115 1.34V.39h-4.903v26.598h4.903V13.335c1.094-.917 2.505-1.446 4.233-1.446 2.717 0 4.163 1.446 4.163 4.41v10.689h4.904v-10.69c0-6.208-2.575-8.819-8.185-8.819zm12.488 19.509h4.904V7.832h-4.904zm2.435-21.237c1.552 0 2.787-1.27 2.787-2.857S58.41 0 56.858 0c-1.553 0-2.823 1.305-2.823 2.893s1.27 2.857 2.823 2.857zm22.754 0c1.552 0 2.787-1.27 2.787-2.857S81.164 0 79.612 0C78.06 0 76.79 1.306 76.79 2.894s1.27 2.857 2.822 2.857zM75.343.636C74.955.53 73.72.142 71.745.142c-3.352 0-6.597 1.2-6.597 6.244v1.446h-2.681v4.057h2.68v13.264c0 3.246-.599 4.304-3.104 4.304-.246 0-.458-.035-.635-.035v3.951a14.46 14.46 0 0 0 2.893.282c3.175 0 5.75-2.046 5.75-6.032V11.889h7.127v15.099h4.903V7.832h-12.03v-.494c0-1.835.424-3.21 3.281-3.21.811 0 1.446.105 2.01.176zm25.153 19.86c-.952 1.447-2.54 2.364-4.304 2.364-3.104 0-5.644-2.47-5.644-5.468 0-3.034 2.54-5.503 5.644-5.503 1.764 0 3.352.917 4.304 2.328l3.175-2.928c-1.729-2.328-4.41-3.81-7.479-3.81-5.785 0-10.477 4.41-10.477 9.913 0 5.468 4.692 9.878 10.477 9.878 2.963 0 5.68-1.482 7.408-3.845zm11.43-12.664h-4.939v19.156h4.94v-9.243c0-2.505 1.34-5.01 4.867-5.01 1.023 0 2.364.283 2.364.283V7.69a3.743 3.743 0 0 0-1.2-.212c-2.716.035-4.727 1.235-6.032 3.034zm23.636 0v13.51c-1.129.953-2.575 1.518-4.34 1.518-2.716 0-4.162-1.447-4.162-4.41V7.83h-4.904v10.62c0 6.209 2.576 8.82 8.185 8.82 1.975 0 3.775-.565 5.221-1.553v1.27h4.904V7.831zM159.304.388v8.537a11.581 11.581 0 0 0-5.574-1.446c-5.327 0-9.701 3.916-9.701 9.913 0 5.891 4.374 9.913 9.701 9.913 2.011 0 4.022-.565 5.574-1.482v1.164h4.939V.388zm-4.692 22.472c-3.28 0-5.715-2.222-5.715-5.468 0-3.14 2.434-5.503 5.715-5.503 1.87 0 3.528.635 4.692 1.623v7.69c-1.164 1.023-2.857 1.658-4.692 1.658z",
     :aria-label "hificrud"}]
   [:g {:fill "none"}
    [:path {:d "M-1.588 0h33.656v33.655H-1.587z"}]
    [:g
     {:stroke "currentColor", :stroke-linecap "round"}
     [:path {:stroke-width "4", :d "M17.344 31.565h-4.207"}]
     [:path
      {:stroke-linejoin "round",
       :stroke-width    "3",
       :d               "M10.877 25.242c-7.532-12.71 1.19-20.965 3.717-22.917a1.052 1.052 0 0 1 1.291 0c2.527 1.952 11.25 10.207 3.718 22.917z"}]
     [:path
      {:stroke-linejoin "round",
       :stroke-width    "3",
       :d
       "m22.58 14.577 3.985 4.78a1.052 1.052 0 0 1 .218.902l-1.624 7.313a1.052 1.052 0 0 1-1.685.593l-3.871-2.924M7.899 14.577l-3.985 4.78a1.052 1.052 0 0 0-.218.902l1.625 7.314a1.052 1.052 0 0 0 1.684.593l3.871-2.924"}]]]
   [:path
    {:fill "currentColor",
     :d
     "M15.358 17.231c1.553 0 2.787-1.27 2.787-2.858 0-1.587-1.234-2.892-2.787-2.892-1.552 0-2.822 1.305-2.822 2.892 0 1.588 1.27 2.858 2.822 2.858z"}]])

(def custom-iconset
  {:customer customer
   :hificrud hificrud})

(defn ico
  ([ico-name]
   (if-let [iconset (namespace ico-name)]
     (get-in iconsets [iconset (keyword (name ico-name))])
     (or
      (get-in custom-iconset [(keyword ico-name)])
      (get-in iconsets [*default-iconset* (keyword ico-name)])))))

(def doc-icon
  {:examples ["[icon/Icon {::icon/name :cloud-thin}]"
              "[icon/Icon {::icon/name :phosphor/cloud-thin :class \"text-primary size-5\"}]"]
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
   [Icon (merge {::name :hificrud
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
