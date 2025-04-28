;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2


(ns app.ui.app-shell
  (:require
   [app.ui.toast :as toast]
   [app.ui.button :as btn]
   [app.ui.core :as uic]
   [hyperlith.core :as h]
   [app.ui.icon :as icon]))

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

(defn theme-toggle
  "Theme toggle button"
  []
  (let [$icon-cls "h-6 w-6 fill-accent stroke-accent transition group-hover:fill-accent/90 group-hover:stroke-accent/90"]
    (h/html
     [:button {:type                   "button"
               :class                  (uic/cs
                                        "block text-sm flex gap-2 w-full cursor-pointer group px-3 py-2 backdrop-blur-sm"
                                        "transition shadow-lg shadow-primary/5 ring-primary/5 text-accent-foreground hover:bg-accent hover:text-accent-foreground")
               :data-attr-aria-pressed "$_darkmode ? 'true' : 'false'"
               :data-on-click          "$_darkmode = !$_darkmode;"}
      [icon/Icon {::icon/name :sun-bold
                  :class      (uic/cs "block dark:hidden" $icon-cls)}]
      [icon/Icon {::icon/name :moon-stars-bold
                  :class      (uic/cs "hidden dark:block" $icon-cls)}]
      "Toggle Dark Mode"])))

(defn avatar-img [avatar class]
  [:img {:class class
         :src   (if avatar
                  avatar
                  "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80")}])

(defn user-menu-sections
  []
  [{:items [{:label "My Profile" :href "#"}
            {:label "Logout" :href "/logout"}]}
   ;; {:items []}
   ])

(defn user-menu-item
  [idx {:keys [label href attrs]}]
  (h/html
   [:a (uic/merge-attrs attrs
                         ;; relative flex cursor-default select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50 [&>svg]:size-4 [&>svg]:shrink-0
                        :href href :class (uic/cs
                                            ;; "text-gray-700 hover:bg-gray-100 hover:text-gray-900 "
                                           "transition-colors focus:bg-accent focus:text-accent-foreground "
                                           "hover:bg-accent hover:text-accent-foreground "
                                           "block px-4 py-2 text-sm")
                        :role "menuitem"
                        :id (str "user-menu-item-" idx))
    label]))

(defn user-menu-section
  [section]
  (h/html
   [:div {:class "py-1" :role "none"}
    (map-indexed user-menu-item (:items section))]))

(defn user-account-actions
  []
  [:div {:id       "user-account-actions"
         :class    "relative inline-block px-2 text-left"
         :data-ref "desktop-user-menu" :data-on-load (format "ActionMenuPopover($%s)" "desktop-user-menu")}
   (avatar-img nil "h-8 w-8 mt-2 rounded-full bg-gray-300 only-collapsed")
   [:div {:class "only-expanded duration-100"}
    [:button {:type          "button"
              :id            "desktop-user-menu-button"
              :popovertarget "desktop-user-menu"
              :class         (uic/cs
                              "group w-full rounded-md px-3.5 py-2 text-left text-sm font-medium focus:outline-hidden focus:ring-2 focus:ring-offset-2"
                              "active:bg-sidebar-accent active:text-sidebar-accent-foreground "
                              "hover:bg-sidebar-accent hover:text-sidebar-accent-foreground "
                              "focus:ring-sidebar-accent-foreground ")
              :aria-expanded "false" :aria-haspopup "true"}
     [:span {:class "flex w-full items-center justify-between"}
      [:span {:class "flex min-w-0 items-center justify-between space-x-3"}
       (avatar-img nil "h-10 w-10 shrink-0 rounded-full ")
       [:span {:class "flex min-w-0 flex-1 flex-col"}
        [:span {:class "truncate text-sm font-medium "}
         "Alice Admin"]
        [:span {:class "truncate text-sm text-sidebar-foreground/50"}
         "admin"]]]
      [icon/Icon {::icon/name :caret-down :class "h-5 w-5 shrink-0 text-gray-400 group-hover:text-sidebar-accent-foreground"}]]]]
   [:div {:id                         "desktop-user-menu"
          :popover                    true
          :role                       "menu"
          :data-match-reference-width true
          :aria-orientation           "vertical"
          :aria-labelledby            "desktop-user-menu-button"
          :tabindex                   "-1"
          :class                      (uic/cs
                                       ;; z-50 max-h-[var(--radix-dropdown-menu-content-available-height)] overflow-y-auto overflow-x-hidden border bg-popover p-1 text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 origin-[--radix-dropdown-menu-content-transform-origin] w-[--radix-dropdown-menu-trigger-width] min-w-56 rounded-lg
                                       "animate-entry absolute right-0 left-0 z-10 mt-1 origin-top divide-y  rounded-md  shadow-lg ring-1  ring-muted focus:outline-hidden"
                                       ;; "bg-white ring-black/5 divide-gray-200"
                                       "bg-popover text-popover-foreground divide-muted")}
    (map user-menu-section (user-menu-sections))
    [:div {:class "py-1"} (theme-toggle)]]])

(defn nav-item
  [current-route {:keys [label icon href route-name]}]
  (let [active? (= current-route route-name)]
    (h/html
     [:a {:href href
          :class
          (uic/cs "flex items-center px-2 py-2 text-sm text-sidebar-foreground"
                  (if active?
                    "bg-sidebar-primary text-sidebar-primary-foreground font-semibold hover:bg-sidebar-primary hover:text-sidebar-primary-foreground focus:bg-sidebar-primary focus:text-sidebar-primary-foreground"
                    "hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus:bg-sidebar-accent focus:text-sidebar-accent-foreground"))}

      (when icon
        [icon/Icon {::icon/name icon :class (uic/cs "mr-3 shrink-0 h-6 w-6")}])
      [:span {:class "only-expanded"} label]])))

(defn vertical-navigation [{:app/keys [current-route]}]
  (map (partial nav-item current-route) nav-data))

(defn mobile-menu
  [req]
  [:dialog {:id "mobile-hamburger-menu", :class "slide-out relative lg:hidden" :data-ref "mobile-hamburger-menu" :popover true}
   [:section {:class "fixed inset-0 flex max-w-xs"}
    [:div {:class "relative flex w-full max-w-xs flex-1 flex-col bg-sidebar pt-5 pb-4"}
     [:div {:class "absolute top-0 right-0 -mr-12 pt-2"}
      [:button {:type          "button"
                :aria-controls "mobile-hamburger-menu"
                :data-on-click "$mobile-hamburger-menu.hidePopover()"
                :class         "ml-1 flex h-10 w-10 items-center justify-center rounded-full focus:outline-hidden focus:ring-2 focus:ring-inset focus:ring-sidebar-accent-foreground"}
       [:span {:class "sr-only"} "Close sidebar"]
       [icon/Icon {::icon/name :x :class "h-6 w-6 text-sidebar-accent-foreground"}]]]
     [:div {:class "flex shrink-0 items-center px-4"}
      [:a {:href "/"}
       [icon/Logomark {:class "h-8 w-auto text-sidebar-accent-foreground fill-sidebar-accent-foreground"}]]]
     [:div {:class "mt-5 h-0 flex-1 overflow-y-auto"}
      [:nav {:class "px-2"}
       [:div {:class "space-y-1"}
        (vertical-navigation req)
        (theme-toggle)]]]]
    ;; Dummy element to force sidebar to shrink to fit close icon
    [:div {:class         "w-14 shrink-0" :aria-hidden "true"
           :data-on-click "$mobile-hamburger-menu.hidePopover()"}]]])

(defn desktop-sidebar  [req]
  (h/html
    [:div {:data-signals-_sidebar.expanded "true"}
     [:div {:id                   "desktop-sidebar-menu"
            :data-class-collapsed "!$_sidebar.expanded"
            :class
            (uic/cs
             "hidden"
             "no-scrollbar"
             "shrink-0 border-r border-sidebar-border sm:translate-x-0 transition-all duration-200"
             "lg:fixed lg:inset-y-0 lg:flex lg:w-64 lg:flex-col lg:border-r lg:bg-sidebar lg:pt-5 lg:pb-4 lg:translate-x-0")}

      [:div {:class "flex shrink-0 items-center px-6 sidebar-logo-container"}
       [:a {:href "/" :class ""}
        [icon/Logomark {:data-class "{'lg:hidden': $_sidebar.expanded, 'lg:cloak': false}"
                        :class      "h-8 w-auto text-sidebar-accent-foreground fill-sidebar-accent-foreground lg:cloak"}]
        [icon/Logotype {:data-class "{'lg:hidden': !$_sidebar.expanded}"
                        :class      "h-8 w-auto text-sidebar-accent-foreground fill-sidebar-accent-foreground"}]]]

      [:div {:class "mt-5 flex h-0 flex-1 flex-col overflow-y-auto overflow-x-hidden pt-1 sidebar-scroll-container"}
       (user-account-actions)
       [:nav
        [:div {:class "space-y-1"}
         (vertical-navigation req)]]
       [:div {:class "flex items-end justify-end"}
        [btn/Button {::btn/intent   :ghost
                     :data-class    "{'rotate-180': !$_sidebar.expanded}"
                     :data-on-click "$_sidebar.expanded = !$_sidebar.expanded"}
         [icon/Icon {::icon/name :arrow-left :class "w-6 h-6 hidden lg:block"}]]]]]]))
(defn app-container
  [body]
  (h/html
    [:div {:id                            "app-container"
           :data-class-app_container_wide "!$_sidebar.expanded"
           :class                         "flex flex-col lg:pl-64 transition-all"}
     [:div {:class "sticky top-0 z-10 flex h-16 shrink-0 border-b border-sidebar-border bg-sidebar lg:hidden"}
      [:button {:type          "button" :class "border-r border-accent text-accent-foreground px-4 focus:outline-hidden focus:ring-2 focus:ring-inset focus:ring-accent-foreground lg:hidden"
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
                    :class                    "flex max-w-xs items-center rounded-full bg-white text-sm focus:outline-hidden focus:ring-2 focus:ring-accent-foreground focus:ring-offset-2" :id "user-menu-button" :aria-expanded "false" :aria-haspopup "true"}
           [:span {:class "sr-only"} "Open user menu"]

           (avatar-img nil "h-8 w-8 rounded-full")]]
         [:div {:id    "user-menu"                                                                                                                                               :data-action-menu true
                :class "hidden absolute right-0 z-10 mt-2 w-48 origin-top-right divide-y divide-gray-200 rounded-md bg-white shadow-lg ring-1 ring-black/5 focus:outline-hidden" :role             "menu" :aria-orientation "vertical" :aria-labelledby "user-menu-button" :tabindex "-1"}
          (map user-menu-section (user-menu-sections))]]]]]
     (if (= :main (first body))
       body
       [:main {:class "flex-1" :id "main"}
        body])]))

(defn app-shell [{:app/keys [tab-state] :as req} body]
  (h/html
    [:main#morph.main {:data-signals-tab-id__case.kebab (format "'%s'" (:app/tab-id req))}
     (mobile-menu req)
     (desktop-sidebar req)
     (app-container body)
     [toast/Notifications {::toast/notifications (:notifications tab-state)
                           ::toast/close-command :home/clear-notification}]]))

(h/refresh-all!)
