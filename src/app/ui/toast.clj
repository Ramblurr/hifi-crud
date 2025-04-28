(ns app.ui.toast
  (:require
   [starfederation.datastar.clojure.expressions :refer [->expr]]
   [app.ui.icon :as icon]
   [app.ui.core :as uic]
   [dev.onionpancakes.chassis.compiler :as cc]
   [dev.onionpancakes.chassis.core :as c]))

(def variants {:default     "border bg-background text-foreground ring-foreground/5"
               :destructive "destructive border-destructive bg-destructive text-destructive-foreground ring-destructive"})

(def doc-notification
  {:examples ["[toast/Notification {:id :some-id ::toast/title \"A title\" ::toast/body \"A message\"}]"
              "[toast/Notification {::toast/variant :destructive  :id :some-id ::toast/body \"Uhoh, something went wrong!\"}]"]
   :ns       *ns*
   :as       'toast
   :name     'Notification
   :desc     "A succinct message that is displayed temporarily."
   :alias    ::notifcation
   :schema
   [:map {}
    [:id {:error/message "a toast notfication requires a unique id"} :string]
    [::variant {:optional true
                :default  :default
                :doc      "Toast variant"}
     [:enum :default :destructive]]

    [::icon {:optional true
             :doc      "A leading icon. See :app.ui/icon"}
     :keyword]
    [::title {:optional true
              :doc      "The title of the notification"}
     :string]
    [::body {:optional true
             :doc      "The body text of the notification"}
     :string]
    [::close-command {:optional true
                      :doc      "The command to execute when the close button is clicked"}
     :qualified-keyword]]})

(def ^{:doc (uic/generate-docstring doc-notification)} Notification
  ::notification)

(defmethod c/resolve-alias ::notification
  [_ {::keys [title body variant close-command]
      :keys  [id]
      :or    {variant :default}
      :as    _attrs} _children]
  (let [$base    (uic/cs
                  "pointer-events-auto transition-all"
                  "w-full max-w-sm overflow-hidden rounded-lg shadow-lg ring-1")
        $variant (get variants variant)]
    (cc/compile
     [:div (uic/attr-map :id    id
                         :class (uic/cs $base $variant "duration-150")
                         :data-class (->expr {"animate-in max-sm:slide-in-from-bottom sm:slide-in-from-right fade-in" (not ($_toast. ~id '.closing))
                                              "animate-out max-sm:slide-out-to-bottom sm:slide-out-to-right fade-out" ($_toast. ~id '.closing)
                                              "hidden"                                                                ($_toast. ~id '.closed)})

                         :data-on-animationend (->expr (when ($_toast. ~id '.closing)
                                                         (set! ($_toast. ~id '.closed) true)
                                                         (set! $notification-id ~id)
                                                         (@post ~(str "/cmd?cmd=" (subs (pr-str close-command) 1))))))

      [:div {:class "p-4"}
       [:div {:class "flex items-start"}
        [:div {:class "shrink-0"}
         [icon/Icon {::icon/name :check-circle :class "size-6 text-green-400"}]]
        [:div {:class "ml-3 w-0 flex-1 pt-0.5"}
         [:p {:class "text-sm font-medium"}
          title]
         [:p {:class "mt-1 text-sm opacity-90"}
          body]]
        [:div {:class "ml-4 flex shrink-0"}
         [:button {:type          "button"
                   :class         (uic/cs
                                   "inline-flex rounded-md focus:ring-2 focus:ring-offset-2 focus:outline-hidden"
                                   "text-foreground/50 hover:text-foreground focus:ring-accent-foreground")
                   :data-on-click (->expr (set! ($_toast. ~id '.closing) true))}
          [:span {:class "sr-only"} "Close"]
          [icon/Icon {::icon/name :x :class "size-5"}]]]]]])))

(def ^{:doc (uic/generate-docstring doc-notification)} NotificationRegion
  ::notification-region)

(defmethod c/resolve-alias ::notification-region
  [_ attrs children]
  (let [toast-sub-signals (reduce #(assoc %1 %2 {:closing false :closed false}) {} (get attrs ::notification-ids))]
    (cc/compile
     [:div (uic/merge-attrs attrs
                            :aria-live               "assertive"
                            :class                   "pointer-events-none fixed inset-0 flex items-end px-4 py-6 sm:items-start sm:p-6"
                            :data-signals__ifmissing (->expr {:_toast ~toast-sub-signals})
                            :data-signals "{'notification-id': null}")
      [:div {:id "notification-container" :class "flex w-full flex-col items-center space-y-4 sm:items-end"}
       children]])))

(def doc-notifications
  {:examples ["[toast/Notifications {::toast/notifications (:notifications tab-state)
                           ::toast/close-command :home/clear-notification}]"]
   :ns       *ns*
   :as       'toast
   :name     'Notifications
   :desc     "A collection of toast notifications"
   :alias    ::notifications
   :schema
   [:map {}
    [::notifications {:doc "A map of notification ids to notification data"}
     [:map-of :string :map]]
    [::close-command {:optional true
                      :doc      "The command to execute when the close button is clicked"}
     :qualified-keyword]]})

(def ^{:doc (uic/generate-docstring doc-notifications)} Notifications
  ::notifications)

(defmethod c/resolve-alias ::notifications
  [_ {::keys [notifications close-command]} _children]
  (let [ids (keys notifications)]
    (cc/compile
     [NotificationRegion {::notification-ids ids}
      (for [{:keys [id title text]} (vals notifications)]
        [Notification {:id id ::title title ::body text ::close-command close-command}])])))
