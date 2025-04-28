;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT


(ns app.ui.action-menu-popover
  (:require
   [dev.onionpancakes.chassis.compiler :as cc]
   [dev.onionpancakes.chassis.core :as c]
   [app.ui.icon :as icon]
   [app.ui.core :as uic]
   [malli.experimental.lite :as l]))

(defn action-menu-item [id idx {:keys [label href attr active? icon tag spinner?]
                                :or   {attr {}
                                       tag  :a}}]
  [tag (merge {:href                                                     href  :class
               (uic/cs
                "text-gray-700 hover:bg-gray-100 hover:text-gray-900 block px-4 py-2 text-sm w-full text-left"
                (when spinner? "button-spinner")
                (when icon "flex gap-1")
                (when active? "font-medium bg-gray-100 text-gray-900 ")) :role "menuitem" :tabindex "-1" :id (str id "-" idx)}
              attr)
   (when icon icon)
   [:span {:class "button-label"} label]
   (when spinner? [icon/Spinner {:class (uic/cs "spinner"
                                                "h-5 w-5"
                                                "text-teal-500")}])])

(defn action-menu-section [idx id section]
  [:div {:class
         ;; maybe add w-48 to make it wider and more clickable?
         ;; mt-2
         (uic/cs (if (zero? idx) "rounded-t-md" "rounded-b-md")
                 "z-10 py-1 divide-y divide-gray-200  bg-white shadow-lg ring-1 ring-black/5 focus:outline-hidden")
         :role "none"}
   (when (:label section)
     [:div {:class "px-4 py-3", :role "none"}
      [:p {:class "truncate text-sm font-medium text-gray-900", :role "none"}  (:label section)]])
   (map-indexed (partial action-menu-item id) (:items section))])

(def doc-action-menu
  {:examples ["[]"
              "[icon/Spinner {:class \"size-5\"}]"]
   :ns       *ns*
   :name     'Spinner
   :alias    ::menu
   :desc     "An action menu drop down.

      :minimal? - when true only shows the button-icon
      :section - a list of maps containing the :items key. The value of :items should be another list of maps
                 the section map can also have the :label key for a section header"
   :schema
   [:map
    [:id {:error/message "the menu requires a unique id"} :string]
    [::button-icon {:optional true} :keyword]
    [::button-icon-class {:optional true} :string]
    [::label {:optional true} :string]
    [::minimal? {:optional true} :boolean]
    [::sections [:vector
                 [:map
                  [:label {:optional true} :string]
                  [:href {:optional true} :string]
                  [:attr {:optional true} :map]
                  [:active? {:optional true} :boolean]
                  [:icon {:optional true} :keyword]
                  [:tag {:optional true} :keyword]]]]]})

(def ^{:doc (uic/generate-docstring doc-action-menu)} ActionMenu
  ::menu)

(defmethod c/resolve-alias
  ::menu
  [_ {:as    attrs
      :keys  [id]
      ::keys [button-icon sections label minimal? button-icon-class]
      :or    {minimal?          false
              button-icon-class "text-gray-900"}} _children]
  (cc/compile
   (let [trigger (str id "_trigger")]
     [:div {:class "flex items-center"}
      [:div (uic/attr-map :class "relative" :data-ref id :data-on-load (format "ActionMenuPopover($%s)" id))
       [:div
        [:button {:id            trigger
                  :popovertarget id
                  :class
                  (uic/cs
                   ;; "dark:bg-gray-800 dark:text-white dark:border-gray-600 dark:hover:bg-gray-700 dark:hover:border-gray-600 dark:focus:ring-gray-700"
                   (when-not minimal?
                     "inline-flex items-center text-gray-900 bg-white border border-gray-300 focus:outline-hidden hover:bg-gray-100 focus:ring-4 focus:ring-gray-200 font-medium rounded-md text-sm px-3 py-1.5"))
                  :type          "button"}
         (when button-icon
           (button-icon {:class (uic/cs  (when minimal? "w-5 h-5")
                                         (when-not minimal? "w-4 h-4 mr-2")
                                         button-icon-class)}))
         (when label label)
         (when-not minimal?
           [icon/Icon {::icon/name :caret-down :class "w-3 h-3 ml-2"}])]]
       [:div (uic/merge-attrs attrs
                              :popover "auto"
                              :anchor trigger
                              :class "animate-entry absolute top-0 left-0 z-10 mt-2 w-max origin-top-right divide-y divide-gray-100 rounded-md bg-white ring-1 shadow-lg ring-black/5 focus:outline-hidden"
                              :id id  :role "menu" :aria-orientation "vertical" :aria-labelledby trigger :tabindex "-1")
        (map-indexed #(action-menu-section %1 id %2) sections)]]])))
