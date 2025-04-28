;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT


(ns app.ui.button
  (:require
   [app.ui.icon :as icon]
   [app.ui.core :as uic]
   [dev.onionpancakes.chassis.compiler :as cc]
   [dev.onionpancakes.chassis.core :as c]))

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

(def button-intents
  {:primary {:classes       "bg-primary text-primary-foreground shadow hover:bg-primary/90"
             :spinner-color "text-primary-foreground"}

   :destructive {:classes "bg-destructive text-destructive-foreground shadow-sm hover:bg-destructive/90"}

   :secondary             {:classes       "bg-secondary text-secondary-foreground shadow-sm hover:bg-secondary/80"
                           :spinner-color "text-secondary-foreground"}
   :secondary-destructive {:classes       "bg-secondary text-destructive shadow-sm hover:bg-destructive/20"
                           :spinner-color "text-red-600"}

   :ghost {:classes "hover:bg-accent hover:text-accent-foreground"}

   :outline             {:classes "border border-input bg-background shadow-sm hover:bg-accent hover:text-accent-foreground"}
   :outline-destructive {:classes "border border-destructive bg-background text-destructive shadow-sm hover:bg-accent hover:text-destructive/80"}

   :link {:classes       "text-primary underline-offset-4 hover:underline"
          :spinner-color "text-primary"
          :no-border     true}

   :link-success     {:classes       "text-green-600 hover:text-green-500 underline-offset-4 hover:underline"
                      :spinner-color "text-green-600"
                      :no-border     true}
   :link-destructive {:classes       "text-destructive underline-offset-4 hover:underline"
                      :spinner-color "text-destructive"
                      :no-border     true}})

(def doc-button
  {:examples ["[btn/Button {::btn/intent :primary :id :some-id} \"Click me!\"]"
              "[btn/Button {::btn/size :large ::btn/icon :star :aria-foo \"bar\"} \"Star Button\"]"]
   :ns       *ns*
   :as       'btn
   :name     'Button
   :desc     "A button, it's for making things happen"
   :alias    ::button
   :schema
   [:map {}
    [::size {:optional true
             :default  :normal
             :doc      "Button size"}
     [:enum :xxsmall :xsmall :small :normal :large]]
    [::intent {:optional true
               :default  :secondary
               :doc      "Button intent"}
     [:enum :primary :destructive :secondary :secondary-destructive :ghost :outline :outline-destructive :link :link-success :link-destructive]]
    [::disabled? {:optional true
                  :doc      "When true, disables button interaction"}
     :boolean]
    [::loading? {:optional true
                 :doc      "When true, shows a spinner"}
     :boolean]
    [::icon {:optional true
             :doc      "A leading icon. See :app.ui/icon"}
     :keyword]
    [::icon-trailing {:optional true
                      :doc      "A trailing icon. See :app.ui/icon"}
     :keyword]]})

(def ^{:doc (uic/generate-docstring doc-button)} Button
  ::button)

(tap>
 (with-meta
   [:portal.viewer/code (uic/generate-docstring doc-button)]
   {:portal.viewer/default :portal.viewer/hiccup}))

(defmethod c/resolve-alias ::button
  [_ {::keys
      [size intent disabled? loading? icon icon-trailing href]
      :keys            [type]
      :or
      {type     :button
       size     :normal
       intent   :secondary
       loading? false} :as attrs} children]
  (uic/validate-opts! doc-button attrs)
  (let [size-data   (get button-sizes size)
        intent-data (get button-intents intent)
        classes     (uic/cs
                     "btn font-semibold relative min-w-fit transition-all relative"
                     (:classes size-data)
                     (:classes intent-data)
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
                            (:spinner-color intent-data))}
       [:use {:href "#svg-sprite-spinner"}]]
      (when (and icon (not loading?))
        [icon/Icon  {::icon/name icon :class (uic/cs "button-icon" (:icon-size size-data) "-ml-0.5") :aria-hidden true}])
      (uic/wrap-text-node :span children)
      (when icon-trailing
        (icon-trailing {:class (uic/cs "button-icon" (:icon-size size-data) "-mr-0.5") :aria-hidden true}))])))
