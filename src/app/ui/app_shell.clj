(ns app.ui.app-shell
  (:require
   [app.ui.core :as uic]
   [hyperlith.core :as h]
   [app.ui.icon :as icon]))

(def $nav-item-current  "inline-flex items-center border-b-2 border-teal-500 px-1 pt-1 text-sm font-medium text-gray-900")
(def $nav-item-inactive  "inline-flex items-center border-b-2 border-transparent px-1 pt-1 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700")

#_(defn nav-item [{:keys [href label current?]}]
    (comment
      "Current: \"border-teal-500 text-gray-900\", Default: \"border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700\"")
    [:a {:href href :class (if current? $nav-item-current $nav-item-inactive)}
     label])

(def nav-data
  [{:href       "#"
    :label      "Dashboard"
    :active?    false
    :route-name :home
    :icon       :gauge}
   {:href       "#"
    :label      "Sales"
    :icon       :cash-register
    :route-name :sales
    :active?    false}
   {:href       "#"
    :label      "Catalog"
    :icon       :book-open
    :route-name :catalog
    :active?    false}
   {:href       "#"
    :label      "Customers"
    :icon       :customer
    :route-name :customers
    :active?    false}
   {:href       "#"
    :label      "Reviews"
    :icon       :chat-text
    :route-name :review
    :active?    false}])

#_(defn nav [{:keys [current-nav]}]
    (let [nav-data (vals (assoc-in nav-data [current-nav :current?] true))]
      [:nav
       {:class "bg-white shadow-sm"}
       [:div
        {:class "mx-auto max-w-7xl px-2 sm:px-6 lg:px-8"}
        [:div
         {:class "relative flex h-16 justify-between"}
         [:div
          {:class "absolute inset-y-0 left-0 flex items-center sm:hidden"}
          (comment "Mobile menu button")
          [:button
           {:type          "button",
            :class
            "relative inline-flex items-center justify-center rounded-md p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-500 focus:ring-2 focus:ring-teal-500 focus:outline-hidden focus:ring-inset",
            :aria-controls "mobile-menu",
            :aria-expanded "false"}
           [:span {:class "absolute -inset-0.5"}]
           [:span {:class "sr-only"} "Open main menu"]
           (comment
             "Icon when menu is closed.\n\n            Menu open: \"hidden\", Menu closed: \"block\"")
           [:svg
            {:class        "block size-6",
             :fill         "none",
             :viewBox      "0 0 24 24",
             :stroke-width "1.5",
             :stroke       "currentColor",
             :aria-hidden  "true",
             :data-slot    "icon"}
            [:path
             {:stroke-linecap  "round",
              :stroke-linejoin "round",
              :d               "M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5"}]]
           (comment
             "Icon when menu is open.\n\n            Menu open: \"block\", Menu closed: \"hidden\"")
           [:svg
            {:class        "hidden size-6",
             :fill         "none",
             :viewBox      "0 0 24 24",
             :stroke-width "1.5",
             :stroke       "currentColor",
             :aria-hidden  "true",
             :data-slot    "icon"}
            [:path
             {:stroke-linecap  "round",
              :stroke-linejoin "round",
              :d               "M6 18 18 6M6 6l12 12"}]]]]
         [:div
          {:class
           "flex flex-1 items-center justify-center sm:items-stretch sm:justify-start"}
          [:div
           {:class "flex shrink-0 items-center"}
           [icon/Icon {::icon/name :rocket :class "h-8 w-auto text-teal-600 fill-teal-900"
                       :alt        "Your Company"}]]
          [:div {:class "hidden sm:ml-6 sm:flex sm:space-x-8"}
           (map nav-item nav-data)]]
         [:div
          {:class    "absolute inset-y-0 right-0 flex items-center pr-2 sm:static sm:inset-auto sm:ml-6 sm:pr-0"
           :data-ref "user-menu" :data-on-load (format "ActionMenuPopover($%s)" "user-menu")}
          [:button
           {:type "button",
            :class
            "relative rounded-full bg-white p-1 text-gray-400 hover:text-gray-500 focus:ring-2 focus:ring-teal-500 focus:ring-offset-2 focus:outline-hidden"}
           [:span {:class "absolute -inset-1.5"}]
           [:span {:class "sr-only"} "View notifications"]
           [:svg
            {:class        "size-6",
             :fill         "none",
             :viewBox      "0 0 24 24",
             :stroke-width "1.5",
             :stroke       "currentColor",
             :aria-hidden  "true",
             :data-slot    "icon"}
            [:path
             {:stroke-linecap  "round",
              :stroke-linejoin "round",
              :d
              "M14.857 17.082a23.848 23.848 0 0 0 5.454-1.31A8.967 8.967 0 0 1 18 9.75V9A6 6 0 0 0 6 9v.75a8.967 8.967 0 0 1-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 0 1-5.714 0m5.714 0a3 3 0 1 1-5.714 0"}]]]
          ;; Profile dropdown
          [:div
           {:class "relative ml-3"}
           [:div
            [:button
             {:type          "button", :class "relative flex rounded-full bg-white text-sm focus:ring-2 focus:ring-teal-500 focus:ring-offset-2 focus:outline-hidden",
              :id            "user-menu-button",
              :popovertarget "user-menu"}
             [:span {:class "absolute -inset-1.5"}]
             [:span {:class "sr-only"} "Open user menu"]
             [:img
              {:class "size-8 rounded-full",
               :src
               "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80",
               :alt   ""}]]]
           (comment
             "Dropdown menu, show/hide based on menu state.\n\n            Entering: \"transition ease-out duration-200\"\n              From: \"transform opacity-0 scale-95\"\n              To: \"transform opacity-100 scale-100\"\n            Leaving: \"transition ease-in duration-75\"\n              From: \"transform opacity-100 scale-100\"\n              To: \"transform opacity-0 scale-95\"")
           [:div
            {:class            "animate-entry absolute right-0 z-10 mt-2 w-48 origin-top-right rounded-md bg-white py-1 shadow-lg ring-1 ring-black/5 focus:outline-hidden",
             :popover          true
             :id               "user-menu"
             :role             "menu"
             :aria-orientation "vertical"
             :aria-labelledby  "user-menu-button"
             :tabindex         "-1"}
            (comment
              "Active: \"bg-gray-100 outline-hidden\", Not Active: \"\"")
            [:a
             {:href     "#",
              :class    "block px-4 py-2 text-sm text-gray-700",
              :role     "menuitem",
              :tabindex "-1",
              :id       "user-menu-item-0"}
             "Your Profile"]
            [:a
             {:href     "#",
              :class    "block px-4 py-2 text-sm text-gray-700",
              :role     "menuitem",
              :tabindex "-1",
              :id       "user-menu-item-1"}
             "Settings"]
            [:a
             {:href     "#",
              :class    "block px-4 py-2 text-sm text-gray-700",
              :role     "menuitem",
              :tabindex "-1",
              :id       "user-menu-item-2"}
             "Sign out"]]]]]]
       (comment "Mobile menu, show/hide based on menu state.")
       #_[:div
          {:class "sm:hidden", :id "mobile-menu"}
          [:div
           {:class "space-y-1 pt-2 pb-4"}
           (comment
             "Current: \"bg-teal-50 border-teal-500 text-teal-700\", Default: \"border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700\"")
           [:a
            {:href "#",
             :class
             "block border-l-4 border-teal-500 bg-teal-50 py-2 pr-4 pl-3 text-base font-medium text-teal-700"}
            "Dashboard"]
           [:a
            {:href "#",
             :class
             "block border-l-4 border-transparent py-2 pr-4 pl-3 text-base font-medium text-gray-500 hover:border-gray-300 hover:bg-gray-50 hover:text-gray-700"}
            "Team"]
           [:a
            {:href "#",
             :class
             "block border-l-4 border-transparent py-2 pr-4 pl-3 text-base font-medium text-gray-500 hover:border-gray-300 hover:bg-gray-50 hover:text-gray-700"}
            "Projects"]
           [:a
            {:href "#",
             :class
             "block border-l-4 border-transparent py-2 pr-4 pl-3 text-base font-medium text-gray-500 hover:border-gray-300 hover:bg-gray-50 hover:text-gray-700"}
            "Calendar"]]]]))
(defn avatar-img [avatar class]
  [:img {:class class
         :src   (if avatar
                  avatar
                  "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80")}])

(defn user-menu-sections
  []
  [{:items [{:label "My Profile" :href "#"}]}
   {:items [{:label "Logout" :href "/logout"}]}])

(defn user-menu-item
  [idx {:keys [label href]}]
  (h/html
   [:a {:href href :class "text-gray-700 hover:bg-gray-100 hover:text-gray-900 block px-4 py-2 text-sm" :role "menuitem" :tabindex "-1" :id (str "user-menu-item-" idx)}
    label]))

(defn user-menu-section
  [section]
  (h/html
   [:div {:class "py-1" :role "none"}
    (map-indexed user-menu-item (:items section))]))

(defn user-account-actions
  []
  [:div {:class    "relative inline-block px-2 text-left"
         :data-ref "desktop-user-menu" :data-on-load (format "ActionMenuPopover($%s)" "desktop-user-menu")}
   (avatar-img nil "h-8 w-8 mt-2 rounded-full bg-gray-300 only-collapsed")
   [:div {:class "only-expanded duration-100"}
    [:button {:type          "button"
              :id            "desktop-user-menu-button"
              :popovertarget "desktop-user-menu"
              :class         (uic/cs
                              "group w-full rounded-md bg-gray-100 px-3.5 py-2 text-left text-sm font-medium text-gray-700 hover:bg-gray-200 focus:outline-hidden focus:ring-2 focus:ring-teal-500 focus:ring-offset-2 focus:ring-offset-gray-100")
              :aria-expanded "false" :aria-haspopup "true"}
     [:span {:class "flex w-full items-center justify-between"}
      [:span {:class "flex min-w-0 items-center justify-between space-x-3"}
       (avatar-img nil  "h-10 w-10 shrink-0 rounded-full bg-gray-300")
       [:span {:class "flex min-w-0 flex-1 flex-col"}
        [:span {:class "truncate text-sm font-medium text-gray-900"}
         "Alice Admin"]
        [:span {:class "truncate text-sm text-gray-500"}
         "admin"]]]
      [icon/Icon {::icon/name :caret-down :class "h-5 w-5 shrink-0 text-gray-400 group-hover:text-gray-500"}]]]]
   [:div {:id                         "desktop-user-menu"
          :popover                    true
          :role                       "menu"
          :data-match-reference-width true
          :aria-orientation           "vertical"
          :aria-labelledby            "desktop-user-menu-button"
          :tabindex                   "-1"
          :class                      "animate-entry absolute right-0 left-0 z-10 mt-1 origin-top divide-y divide-gray-200 rounded-md bg-white shadow-lg ring-1 ring-black/5 focus:outline-hidden"}
    (map user-menu-section (user-menu-sections))]])

(defn nav-item
  [current-route {:keys [label icon href route-name]}]
  (let [active? (= current-route route-name)]
    (h/html
     [:a {:href href
          :class
          (uic/cs "flex items-center px-2 py-2 text-sm"
                  #_"-center px-2 py-2 text-sm font-medium rounded-md group cursor-pointer"
                  (if active?
                    "bg-gray-200 text-teal-700 font-semibold"
                    "text-gray-700 hover:text-gray-900 hover:bg-gray-50"))}

      (when icon
        [icon/Icon {::icon/name icon :class (uic/cs "mr-3 shrink-0 h-6 w-6"
                                                    (if active?
                                                      "text-teal-700"
                                                      "text-gray-400 group-hover:text-gray-500"))}])
      [:span {:class "only-expanded"} label]])))

(defn vertical-navigation [{:app/keys [current-route]}]
  (map (partial nav-item current-route) nav-data))

(defn mobile-menu
  [req]
  [:dialog {:id "mobile-hamburger-menu", :class "slide-out relative lg:hidden" :data-ref "mobile-hamburger-menu" :popover true}
   [:section {:class "fixed inset-0 flex max-w-xs"}
    [:div {:class "relative flex w-full max-w-xs flex-1 flex-col bg-white pt-5 pb-4"}
     [:div {:class "absolute top-0 right-0 -mr-12 pt-2"}
      [:button {:type          "button"
                :aria-controls "mobile-hamburger-menu"
                :data-on-click "$mobile-hamburger-menu.hidePopover()"
                :class         "ml-1 flex h-10 w-10 items-center justify-center rounded-full focus:outline-hidden focus:ring-2 focus:ring-inset focus:ring-white"}
       [:span {:class "sr-only"} "Close sidebar"]
       [icon/Icon {::icon/name :x :class "h-6 w-6 text-white"}]]]
     [:div {:class "flex shrink-0 items-center px-4"}
      [:a {:href "/"}
       [icon/Logomark {:class "h-8 w-auto text-teal-600 fill-teal-600 logotype-dark"}]]]
     [:div {:class "mt-5 h-0 flex-1 overflow-y-auto"}
      [:nav {:class "px-2"}
       [:div {:class "space-y-1"}
        (vertical-navigation req)]]]]
    ;; Dummy element to force sidebar to shrink to fit close icon
    [:div {:class         "w-14 shrink-0" :aria-hidden "true"
           :data-on-click "$mobile-hamburger-menu.hidePopover()"}]]])

(defn desktop-menu  [req]
  (h/html
   [:div {:data-signals-_sidebar.expanded "true"}
    [:div {:id                   "desktop-sidebar-menu"
           :data-class-collapsed "!$_sidebar.expanded"
           :class
           (uic/cs
            "hidden lg:fixed lg:inset-y-0 lg:flex lg:w-64 lg:flex-col lg:border-r lg:border-gray-200 lg:bg-gray-100 lg:pt-5 lg:pb-4"
            "lg:translate-x-0"
            "no-scrollbar"
            "shrink-0 border-r border-gray-200 sm:translate-x-0 transition-all duration-200")}

     [:div {:class "flex shrink-0 items-center px-6 sidebar-logo-container"}
      [:a {:href "/" :class ""}
       [icon/Logomark {:data-class "{'lg:hidden': $_sidebar.expanded, 'lg:cloak': false}"
                       :class      "h-8 w-auto text-teal-600 logotype-dark lg:cloak"}]
       [icon/Logotype {:data-class "{'lg:hidden': !$_sidebar.expanded}"
                       :class      "h-8 w-auto text-teal-600 logotype-dark"}]]]

     [:div {:class "mt-5 flex h-0 flex-1 flex-col overflow-y-auto overflow-x-hidden pt-1 sidebar-scroll-container"}
      (user-account-actions)
      [:nav
       [:div {:class "space-y-1"}
        (vertical-navigation req)]]
      [:div {:class "flex items-end justify-end"}
       [:button {:class         "px-3 py-2 text-gray-700 hover:text-gray-900 hover:bg-gray-50 rounded-md sidebar-open-close-button rotate-0 "
                 :data-on-click "$_sidebar.expanded = !$_sidebar.expanded"}
        [icon/Icon {::icon/name :arrow-left :class "w-6 h-6 hidden lg:block"}]]]]]]))
(defn app-container
  [body]
  (h/html
   [:div {:id                            "app-container"
          :data-class-app_container_wide "!$_sidebar.expanded"
          :class                         "flex flex-col lg:pl-64 transition-all"}
    [:div {:class "sticky top-0 z-10 flex h-16 shrink-0 border-b border-gray-200 bg-white lg:hidden"}
     [:button {:type          "button" :class "border-r border-gray-200 px-4 text-gray-500 focus:outline-hidden focus:ring-2 focus:ring-inset focus:ring-teal-500 lg:hidden"
               :popovertarget "mobile-hamburger-menu"}
      [:span {:class "sr-only"} "Open sidebar"]
      [icon/Icon {::icon/name :hamburger :class "h-6 w-6"}]]
     [:div {:class "flex flex-1 justify-between px-4 sm:px-6 lg:px-8"}
      [:div {:class "flex flex-1"}]
      [:div {:class "flex items-center"}
       [:div {:class "relative ml-3"}
        [:div
         [:button {:type                     "button"
                   :data-action-menu-trigger "#user-menu"
                   :class                    "flex max-w-xs items-center rounded-full bg-white text-sm focus:outline-hidden focus:ring-2 focus:ring-teal-500 focus:ring-offset-2" :id "user-menu-button" :aria-expanded "false" :aria-haspopup "true"}
          [:span {:class "sr-only"} "Open user menu"]

          (avatar-img nil "h-8 w-8 rounded-full")]]
        [:div {:id    "user-menu"                                                                                                                                               :data-action-menu true
               :class "hidden absolute right-0 z-10 mt-2 w-48 origin-top-right divide-y divide-gray-200 rounded-md bg-white shadow-lg ring-1 ring-black/5 focus:outline-hidden" :role             "menu" :aria-orientation "vertical" :aria-labelledby "user-menu-button" :tabindex "-1"}
         (map user-menu-section (user-menu-sections))]]]]]
    (if (= :main (first body))
      body
      [:main {:class "flex-1" :id "main"}
       body])]))

(defn notification [id title text]
  (h/html
   [:div {:id id :class "notification-transition pointer-events-auto w-full max-w-sm overflow-hidden rounded-lg bg-white shadow-lg ring-1 ring-black/5"}
    [:div {:class "p-4"}
     [:div {:class "flex items-start"}
      [:div {:class "shrink-0"}
       [icon/Icon {::icon/name :check-circle :class "size-6 text-green-400"}]]
      [:div {:class "ml-3 w-0 flex-1 pt-0.5"}
       [:p {:class "text-sm font-medium text-gray-900"}
        title]
       [:p {:class "mt-1 text-sm text-gray-500"}
        text]]
      [:div {:class "ml-4 flex shrink-0"}
       [:button {:type          "button" :class "inline-flex rounded-md bg-white text-gray-400 hover:text-gray-500 focus:ring-2 focus:ring-teal-500 focus:ring-offset-2 focus:outline-hidden"
                 :data-on-click (format "$notification-id = '%s'; %s" id (uic/dispatch :home/clear-notification))}
        [:span {:class "sr-only"} "Close"]
        [icon/Icon {::icon/name :x :class "size-5"}]]]]]]))

(defn notification-region [notifications]
  (h/html
   [:div {:aria-live               "assertive"
          :class                   "pointer-events-none fixed inset-0 flex items-end px-4 py-6 sm:items-start sm:p-6"
          :data-signals__ifmissing "{'notification-id': null}"}
    [:div {:id "notification-container" :class "flex w-full flex-col items-center space-y-4 sm:items-end"}
     (for [notif notifications]
       (let [{:keys [id title text]} notif]
         (notification id title text)))]]))

(defn app-shell [{:keys [tab-state] :as req} body]
  (h/html
   [:main#morph.main {:data-signals-tab-id__case.kebab (format "'%s'" (:app/tab-id req))}
    (mobile-menu req)
    (desktop-menu req)
    (app-container body)
    (notification-region
     (vals (:notifications tab-state)))]))

(h/refresh-all!)
