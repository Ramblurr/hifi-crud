(ns app.ui.button
  (:require
   [app.ui.core :as uic]
   [dev.onionpancakes.chassis.compiler :as cc]
   [dev.onionpancakes.chassis.core :as c]
   [malli.experimental.lite :as l]))

(def button-sizes
  {:xxsmall {:classes   "rounded-sm px-2 py-1 text-xs"
             :gap       "gap-x-1.5"
             :icon-size "size-3"}
   :xsmall  {:classes   "rounded-sm px-2 py-1 text-sm"
             :gap       "gap-x-1.5"
             :icon-size "size-4"}
   :small   {:classes   "rounded-md px-2.5 py-1.5 text-sm"
             :gap       "gap-x-1.5"
             :icon-size "size-5"}
   :normal  {:classes   "rounded-md px-3 py-2 text-sm"
             :gap       "gap-x-1.5"
             :icon-size "size-5"}
   :large   {:classes   "rounded-md px-3.5 py-2.5 text-sm"
             :gap       "gap-x-2"
             :icon-size "size-5"}})

(def button-priorities
  {:primary               {:classes       "bg-teal-600 text-white shadow-xs hover:bg-teal-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-600"
                           :dark          "dark:bg-teal-500 dark:hover:bg-teal-400 dark:focus-visible:outline-teal-500"
                           :spinner-color "text-white"}
   :secondary             {:classes       "bg-white text-gray-900 ring-1 ring-gray-300 ring-inset shadow-xs hover:bg-gray-50"
                           :dark          "dark:bg-white/10 dark:text-white dark:ring-0 dark:hover:bg-white/20"
                           :spinner-color "text-gray-900"}
   :secondary-destructive {:classes       "bg-white text-red-600 ring-1 ring-red-300 ring-inset shadow-xs hover:bg-red-50"
                           :dark          "dark:bg-white/10 dark:text-red-400 dark:ring-0 dark:hover:bg-red-900/20"
                           :spinner-color "text-red-600"}
   :link                  {:classes       "text-teal-600 hover:text-teal-500 underline-offset-4 hover:underline"
                           :dark          "dark:text-teal-400 dark:hover:text-teal-300"
                           :spinner-color "text-teal-600"
                           :no-border     true}

   :link-success     {:classes       "text-green-600 hover:text-green-500 underline-offset-4 hover:underline"
                      :dark          "dark:text-green-400 dark:hover:text-green-300"
                      :spinner-color "text-green-600"
                      :no-border     true}
   :link-destructive {:classes       "text-red-600 hover:text-red-500 underline-offset-4 hover:underline"
                      :dark          "dark:text-red-400 dark:hover:text-red-300"
                      :spinner-color "text-red-600"
                      :no-border     true}})

(def Button
  (with-meta
    {:btn/size          (l/optional [:enum :xxsmall :xsmall :small :normal :large])
     :btn/priority      (l/optional [:enum :primary :secondary :secondary-destructive :link :link-success :link-destructive])
     :btn/disabled?     (l/optional :boolean)
     :btn/loading?      (l/optional :boolean)
     :btn/icon          (l/optional fn?)
     :btn/icon-trailing (l/optional fn?)}
    {:name :app.ui/button}))

(defmethod c/resolve-alias :app.ui/button
  [_ {:btn/keys
      [size priority disabled? loading? icon icon-trailing href]
      :keys            [type]
      :or
      {type     :button
       size     :normal
       priority :secondary
       loading? false} :as attrs} children]
  (uic/validate-opts! Button attrs)
  (let [anchor?       (some? href)
        size-data     (get button-sizes size)
        priority-data (get button-priorities priority)
        classes       (uic/cs
                       "btn font-semibold relative min-w-fit transition-all relative"
                       (:classes size-data)
                       (:classes priority-data)
                       (:dark priority-data)
                       (when loading? "spinning")
                       (when (or icon icon-trailing loading?) "inline-flex items-center")
                       (when (or icon icon-trailing) (:gap size-data))
                       (when disabled? "opacity-50 cursor-not-allowed")
                       "disabled:opacity-50 disabled:cursor-not-allowed")]
    (cc/compile
     [:button  (uic/merge-attrs attrs
                                :type type
                                :class classes)
      [:svg {:class (uic/cs "spinner animate-spin"
                            (:icon-size size-data)
                            (:spinner-color priority-data))}
       [:use {:href "#svg-sprite-spinner"}]]
      (when (and icon (not loading?))
        [:app.ui/icon  {:ico/name icon :class (uic/cs "button-icon" (:icon-size size-data) "-ml-0.5") :aria-hidden true}])
      (uic/wrap-text-node :span children)
      (when icon-trailing
        (icon-trailing {:class (uic/cs "button-icon" (:icon-size size-data) "-mr-0.5") :aria-hidden true}))])))
