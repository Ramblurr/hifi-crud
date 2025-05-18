;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns app.ui.form
  (:require
   [hifi.datastar :as datastar]
   [dev.onionpancakes.chassis.compiler :as cc]
   [dev.onionpancakes.chassis.core :as c]
   [app.ui.core :as uic]
   [clojure.string :as str]
   [malli.experimental.lite :as l]))

;; HACK(hyperlith): shouldn't depend on the hyperlith.impl.json ns

(def NameAttribute [:name {:doc           "the name html attribute, required for all form fields"
                           :error/message "is required. All form fields require a name attribute as a string or keyword."} [:or :string :keyword]])
(def FormSchema
  {:form/key         :keyword
   :submit-command   :keyword
   :validate-command (l/optional :keyword)
   :fields           :map})

(defn data-class [m]
  (str "{"
       (->> m
            (map (fn data-class [[expr classes]]
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

(def doc-form
  {:examples ["[form/Form {::form/form {::form/key :my-form :submit-command :submit-form :fields {:name \"Example\"}}} [...]]"]
   :ns       *ns*
   :name     'Form
   :desc     "An HTML <form> that manages datastar signals"
   :alias    ::form
   :schema
   [:map {}
    [::form {:doc "Form configuration"} (l/schema FormSchema)]]})

(def ^{:doc (uic/generate-docstring doc-form)}
  Form
  ::form)

(defmethod c/resolve-alias ::form
  [_ {::keys [form] :as attrs} children]
  (uic/validate-opts! doc-form attrs)
  (assert form)
  (cc/compile
   [:form (uic/merge-attrs attrs
                           :data-signals__ifmissing (datastar/edn->json
                                                     {(:form/key form)
                                                      (merge (-> form :fields)
                                                             {:touched (zipmap (-> form :fields keys) (repeat 0))
                                                              :error   (-> (-> form :fields keys)
                                                                           (zipmap (repeat nil))
                                                                           (assoc :_top nil))})})
                           :data-on-submit (uic/dispatch (:submit-command form)))
    children]))

(def doc-form-actions
  {:examples ["[form/Actions {::form/left [:div \"Cancel\"] ::form/right [:div \"Submit\"]}]"]
   :ns       *ns*
   :name     'Actions
   :desc     "Form actions container for form buttons"
   :alias    ::actions
   :schema
   [:map {}
    [::left {:optional true
             :doc      "Content for the left side of the actions row"}
     :any]
    [::right {:optional true
              :doc      "Content for the right side of the actions row"}
     :any]]})

(def ^{:doc (uic/generate-docstring doc-form-actions)}
  Actions
  ::actions)

(defmethod c/resolve-alias  ::actions
  [_ {::keys [left right] :as attrs} _children]
  (uic/validate-opts! doc-form-actions attrs)
  (cc/compile
   [:div {:class "py-5 flex justify-between items-center"
          #_"mt-6 flex items-center justify-end gap-x-6"}
    [:div {:class "flex items-center space-x-3 space-x-4"}
     left]
    [:div {:class "flex justify-end space-x-4"}
     right]]))

(def doc-root-errors
  {:examples ["[form/RootErrors {::form/title \"Errors\" ::form/form form-config}]"]
   :ns       *ns*
   :require  "(require '[app.ui.form :refer [RootErrors]])"
   :name     'RootErrors
   :desc     "Displays top-level form errors (errors not associated with a specific field)"
   :alias    ::errors
   :schema
   [:map {}
    [::title {:optional true
              :doc      "Title for the error section"}
     :string]
    [::form {:doc "Form configuration"}
     (l/schema FormSchema)]]})

(def ^{:doc (uic/generate-docstring doc-root-errors)}
  RootErrors
  ::errors)

;; Renders top-level form errors
(defmethod c/resolve-alias ::errors
  [_ {::keys [form title] :as attrs} children]
  (uic/validate-opts! doc-root-errors attrs)
  (assert form)
  (let [$top-error-signal (str "$" (clojure.core/name (:form/key form)) ".error._top")]
    (cc/compile
     [:div (uic/merge-attrs attrs :class (uic/cs "hidden mt-1 text-destructive")
                            :data-class-hidden (str "!" $top-error-signal))
      (when title
        [:h3 {:class "text-sm font-semibold text-destructive"} title])
      [:p {:class     "mt-1 text-sm/6 text-sm text-destructive"
           :data-text $top-error-signal}]
      [:div {:class (uic/cs "")}
       children]])))

(def doc-form-section
  {:examples ["[form/Section {::form/title \"Contact Info\" ::form/subtitle \"Tell us about yourself\"}]"]
   :ns       *ns*
   :name     'Section
   :desc     "A section within a form with optional title and subtitle"
   :alias    ::section
   :schema
   [:map {}
    [::title {:optional true
              :doc      "Section title"}
     :any]
    [::subtitle {:optional true
                 :doc      "Section subtitle or description"}
     :any]
    [::narrow? {:optional true
                :doc      "When true, uses a narrower grid layout"}
     :boolean]
    [::compact? {:optional true
                 :doc      "When true, uses more compact spacing"}
     :boolean]]})

(def ^{:doc (uic/generate-docstring doc-form-section)}
  Section
  ::section)

(defmethod c/resolve-alias ::section
  [_ {::keys [title subtitle compact? narrow?] :as attrs} children]
  (uic/validate-opts! doc-form-section attrs)
  (cc/compile
   [:div (uic/merge-attrs attrs :class (uic/cs
                                        "border-b border-grid"
                                        (if compact? "pb-6" "pb-12")))
    (when title
      [:h2 {:class "text-base/7 font-semibold"} title])
    (when subtitle
      [:p {:class "mt-1 text-sm/6 text-base text-muted-foreground"} subtitle])
    [:div {:class (uic/cs "grid grid-cols-1 gap-x-6 gap-y-8 "
                          (if narrow? "sm:grid-cols-3" "sm:grid-cols-6")
                          (if compact? "mt-4" "mt-10"))}
     children]]))

(defn control
  [attrs input-fn]
  (cc/compile
   (let [class                                (:class attrs)
         {:keys  [id name]
          ::keys [form label  required? description error variant error-icon?]
          :or    {required?   true
                  error-icon? false}}         attrs
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
         [:label {:for id :class "block text-sm/6 font-medium"} label]
         (when required?
           [:span {:class "text-sm/6" :id required-id}
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
                        :class       "pointer-events-none col-start-1 row-start-1 mr-3 size-5 self-center justify-self-end text-destructive sm:size-4"
                        :aria-hidden "true"
                        :data-show   $error-signal}])]
      (when (and (not error) (not hidden?) description)
        [:p {:class     "mt-2 text-sm text-muted-foreground"
             :id        description-id
             :data-show (str "!" $error-signal)}
         description])
      [:p {:class     "mt-2 text-sm text-destructive" :id error-id
           :data-show $error-signal
           :data-text $error-signal}]])))

(def doc-hidden-input
  {:examples ["[form/HiddenInput {::form/form form-config :name \"secret-value\"}]"]
   :ns       *ns*
   :name     'HiddenInput
   :desc     "A hidden form input that is not visible to users"
   :alias    ::hidden
   :schema
   [:map {}
    [::form {:doc "Form configuration"} (l/schema FormSchema)]
    NameAttribute]})

(def ^{:doc (uic/generate-docstring doc-hidden-input)} HiddenInput
  ::hidden)

(defmethod c/resolve-alias ::hidden
  [_ {:keys [id name] ::keys [form] :as attrs} _]
  (uic/validate-opts! doc-hidden-input attrs)
  (let [id            (or id (str "form-control" (:form/key form) name))
        [signal _]    (field-signal-name form name)
        default-value (get-in form [:fields name])]
    [:input (uic/merge-attrs attrs
                             :type      "hidden"
                             :id        id
                             :value     default-value
                             :data-bind signal)]))

(def doc-form-input
  {:examples ["[form/Input {::form/form form-config ::form/label \"Username\" :name \"username\"}]"]
   :ns       *ns*
   :name     'Input
   :desc     "A standard text input for forms with label, description and error handling"
   :alias    ::input
   :schema
   [:map {}
    [::label {:doc "Label text for the input"}
     :string]
    [::form {:doc "Form configuration"}
     (l/schema FormSchema)]
    [::error {:optional true
              :doc      "Error message to display"}
     :string]
    [::description {:optional true
                    :doc      "Description or help text"}
     :string]
    [::suffix {:optional true
               :doc      "Content to display after the input"}
     :any]
    [::required? {:optional true
                  :default  true
                  :doc      "Whether the field is required"}
     :boolean]
    NameAttribute]})

(def ^{:doc (uic/generate-docstring doc-form-input)}
  Input
  ::input)

(defmethod c/resolve-alias ::input
  [_ attrs _]
  (uic/validate-opts! doc-form-input attrs)
  (cc/compile
   (control attrs
            (fn input-inner [attrs {:keys [required? $error-signal]}]
              [:input (uic/merge-attrs attrs
                                       :required required?
                                       :class (uic/cs "block w-full rounded-md bg-white py-1.5 text-base outline-1 -outline-offset-1 focus:outline-2 focus:-outline-offset-2 sm:text-sm/6")
                                       :data-class
                                       (data-class {$error-signal
                                                    (uic/cs
                                                     "col-start-1 row-start-1 pr-10 pl-3  sm:pr-9"
                                                     "text-destructive outline-destructive focus:outline-destructive")

                                                    (str "!" $error-signal)
                                                    (uic/cs
                                                     "px-3"
                                                     "text-base ring-offset-background"
                                                     "placeholder:text-muted-foreground"
                                                     "focus-visible:outline-ring")})
                                       :data-attr-aria-invalid $error-signal)]))))
