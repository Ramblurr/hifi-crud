(ns app.ui.form
  (:require
   [dev.onionpancakes.chassis.compiler :as cc]
   [dev.onionpancakes.chassis.core :as c]
   [hyperlith.impl.json :as j]
   [app.ui.core :as uic]
   [clojure.string :as str]
   [malli.experimental.lite :as l]))

;; HACK(hyperlith): shouldn't depend on the hyperlith.impl.json ns

(def NameAttribute [:name {:error/message "is required. All form fields require a name attribute as a string or keyword."} [:or :string :keyword]])
(def FormSchema
  {:form/key         :keyword
   :submit-command   :keyword
   :validate-command (l/optional :keyword)
   :fields           :map})

(defn data-class [m]
  (str "{"
       (->> m
            (map (fn [[expr classes]]
                   (format "\"%s\": %s" classes expr)))
            (str/join ","))
       "}"))

(defn field-signal-name [{form-key :form/key :as _form} field-name]
  (when (and field-name form-key)
    (let [top-ns         (str (clojure.core/name form-key))
          field-name-str (if (keyword? field-name)
                           (name field-name)
                           field-name)]
      [(str top-ns "." field-name-str)
       (str top-ns ".error." field-name-str)
       (str top-ns ".touched." field-name-str)])))

(def Form
  (with-meta
    [:map
     [:form/form (l/schema FormSchema)]]
    {:name ::form}))
(defmethod c/resolve-alias ::form
  [_ {:form/keys [form] :as attrs} children]
  (uic/validate-opts! Form attrs)
  (cc/compile
   [:form (uic/merge-attrs attrs
                           :data-signals__ifmissing (j/edn->json
                                                     {(:form/key form)
                                                      (merge (-> form :fields)
                                                             {:touched (zipmap (-> form :fields keys) (repeat 0))
                                                              :error   (-> (-> form :fields keys)
                                                                           (zipmap (repeat nil))
                                                                           (assoc :_top nil))})})
                           :data-on-submit (uic/dispatch (:submit-command form)))
    children]))

(def FormActions
  (with-meta
    [:map
     [:form/left {:optional true} :any]
     [:form/right {:optional true} :any]]
    {:name ::actions}))

(defmethod c/resolve-alias  ::actions
  [_ {:form/keys [left right] :as attrs} _children]
  (uic/validate-opts! FormActions attrs)
  (cc/compile
   [:div {:class "py-5 flex justify-between items-center"
          #_     "mt-6 flex items-center justify-end gap-x-6"}
    [:div {:class "flex items-center space-x-3 space-x-4"}
     left]
    [:div {:class "flex justify-end space-x-4"}
     right]]))

(def FormErrors
  (with-meta
    {:form/title (l/optional :string)
     :form/form  FormSchema}
    {:name ::errors}))

;; Renders top-level form errors
(defmethod c/resolve-alias ::errors
  [_ {:form/keys [form title] :as attrs} children]
  (uic/validate-opts! FormErrors attrs)
  (let [$top-error-signal (str "$" (clojure.core/name (:form/key form)) ".error._top")]
    (cc/compile
     [:div (uic/merge-attrs attrs :class (uic/cs "hidden mt-1 text-red-700")
                            :data-class-hidden (str "!" $top-error-signal))
      (when title
        [:h3 {:class "text-sm font-semibold text-red-700"} title])
      [:p {:class     "mt-1 text-sm/6 text-sm text-red-600"
           :data-text $top-error-signal}]
      [:div {:class (uic/cs "")}
       children]])))

(def FormSection
  (with-meta
    {:section/title    (l/optional :any)
     :section/subtitle (l/optional :any)
     :section/narrow?  (l/optional :boolean)
     :section/compact? (l/optional :boolean)}
    {:name ::section}))

(defmethod c/resolve-alias ::section
  [_ {:section/keys [title subtitle compact? narrow?] :as attrs} children]
  (uic/validate-opts! FormSection attrs)
  (cc/compile
   [:div (uic/merge-attrs attrs :class (uic/cs
                                        "border-b border-gray-900/10"
                                        (if compact? "pb-6" "pb-12")))
    (when title
      [:h2 {:class "text-base/7 font-semibold text-gray-900"} title])
    (when subtitle
      [:p {:class "mt-1 text-sm/6 text-gray-600"} subtitle])
    [:div {:class (uic/cs "grid grid-cols-1 gap-x-6 gap-y-8 "
                          (if narrow? "sm:grid-cols-3" "sm:grid-cols-6")
                          (if compact? "mt-4" "mt-10"))}
     children]]))

(defn control
  [attrs input-fn]
  (cc/compile
   (let [class                                (:class attrs)
         {:keys      [id name]
          :form/keys [form label  required? description error variant error-icon?]
          :or        {required?   true
                      error-icon? false}}     attrs
         id                                   (or id (str "form-control" (:form/key form) name))
         description-id                       (str "_" id "-description")
         required-id                          (str "_" id "-required")
         error-id                             (str "_" id "-error")
         aria-describedby                     (cond
                                                error       error-id
                                                description description-id
                                                required-id required-id
                                                :else       nil)
         [signal error-signal touched-signal] (field-signal-name form name)
         $error-signal                        (str "$" error-signal)
         $touched-signal                      (str "$" touched-signal)
         default-value                        (get-in form [:fields name])
         hidden?                              (= variant :hidden)]
     (assert name "form-controls require an :name")
     [:div {:class (uic/cs class)}
      (when-not hidden?
        [:div  {:class "flex justify-between"}
         [:label {:for id :class "block text-sm/6 font-medium text-gray-900"} label]

         (when required?
           [:span {:class "text-sm/6 text-red-400" :id required-id}
            "required"])])
      [:div {:data-class (data-class {$error-signal "grid grid-cols-1"})
             :class      (uic/cs (when-not (= :hidden variant) "mt-2"))}
       (input-fn (uic/merge-attrs attrs
                                  :aria-describedby aria-describedby
                                  :aria-label (when (= variant :hidden) label)
                                  :data-bind        signal
                                  :data-on-blur (when-let [cmd (:validate-command form)]
                                                  (str $touched-signal "++;" (uic/dispatch cmd)))
                                  :value default-value
                                  :id               id)
                 {:required?      required?
                  :$error-signal  $error-signal
                  :description-id description-id
                  :error          error})
       (when error-icon?
         [:app.ui/icon {:ico/name    :warning-circle-bold
                        :class       "pointer-events-none col-start-1 row-start-1 mr-3 size-5 self-center justify-self-end text-red-500 sm:size-4"
                        :aria-hidden "true"
                        :data-show   $error-signal}])]
      (when (and (not error) (not hidden?) description)
        [:p {:class     "mt-2 text-sm text-gray-500"
             :id        description-id
             :data-show (str "!" $error-signal)}
         description])
      [:p {:class     "mt-2 text-sm text-red-600" :id error-id
           :data-show $error-signal
           :data-text $error-signal}]])))

(def HiddenInput
  (with-meta
    [:map
     [:form/form (l/schema FormSchema)]
     NameAttribute]
    {:name ::hidden}))

(defmethod c/resolve-alias ::hidden
  [_ {:keys [id name] :form/keys [form] :as attrs} _]
  (uic/validate-opts! HiddenInput attrs)
  (let [id            (or id (str "form-control" (:form/key form) name))
        [signal _]    (field-signal-name form name)
        default-value (get-in form [:fields name])]
    [:input (uic/merge-attrs attrs
                             :type      "hidden"
                             :id        id
                             :value     default-value
                             :data-bind signal)]))

(def FormInput
  (with-meta
    [:map
     [:form/label :string]
     [:form/form (l/schema FormSchema)]
     [:form/error {:optional true} :string]
     [:form/description {:optional true} :string]
     [:form/suffix {:optional true} :any]
     [:form/required? {:optional true} :boolean]
     NameAttribute]
    {:name ::input}))

(defmethod c/resolve-alias ::input
  [_ attrs _]
  (uic/validate-opts! FormInput attrs)
  (cc/compile
   (control attrs
            (fn [attrs {:keys [required? $error-signal]}]
              [:input (uic/merge-attrs attrs
                                       :required required?
                                       :class (uic/cs "block w-full rounded-md bg-white py-1.5 text-base outline-1 -outline-offset-1 focus:outline-2 focus:-outline-offset-2 sm:text-sm/6")
                                       :data-class
                                       (data-class {$error-signal           "col-start-1 row-start-1 pr-10 pl-3 text-red-900 outline-red-300 placeholder:text-red-300 focus:outline-red-600 sm:pr-9"
                                                    (str "!" $error-signal) "px-3 text-gray-900 outline-gray-300 placeholder:text-gray-400 focus:outline-teal-600"})
                                       :data-attr-aria-invalid $error-signal)]))))
