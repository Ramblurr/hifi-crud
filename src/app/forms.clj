;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2


(ns app.forms
  (:require
   [app.malli :as s]
   [hyperlith.extras.datahike :as d]
   [medley.core :as medley]))

(defn untouched-fields
  "Get all field values that have not been touched"
  [form-key signals]
  (keys (medley/filter-vals #(== % 0) (-> signals form-key :touched))))

(defn touched-fields
  "Get all field values that have been touched"
  [form-key signals]
  (keys (medley/filter-vals #(> % 0) (-> signals form-key :touched))))

(defn all-fields
  "Get all field values for a form"
  [form-key signals]
  (keys (-> signals form-key :touched)))

(defn merge-errors
  "Returns an effect to merge-signals to display the error messages for a form"
  ([signals form-key error]
   (merge-errors signals form-key error nil))
  ([signals form-key error {:as _opts :keys [only] :or {only :all}}]
   (let [_top   (:malli/error error (:_top error))
         fields (-> (if (= only :touched)
                      (touched-fields form-key signals)
                      (all-fields form-key signals))
                    (zipmap (repeat nil))
                    (assoc :_top _top))]
     {:effect/kind :d*/merge-signals
      :effect/data {form-key {:error (merge fields error)}}})))

(defn clear-form-errors
  "Returns an effect to clear the form errors by merging the appropriate signals as nil"
  [signals form-key]
  (merge-errors signals form-key nil))

(defn unique-attr-available? [attr db value]
  (nil? (d/find-by db attr value [attr])))

(defn remove-untouched-errors
  "Remove errors from the validation result for fields that haven't been touched by the user yet"
  [form-key signals validation-result]
  (let [errors (apply dissoc validation-result (untouched-fields form-key signals))]
    (when errors
      errors)))

(defn form-options
  "Given a `form-schema`, a malli schema with some extra well-known properties, returns a map with form options.

  Well known properties defined by the user are:

  :form/key - a non-qualified keyword, unique across the application, that identifies the form

  This function adds the following based on parsing the schema:

  :form/fields - a list of fields in the form
  :form/schema - the original malli schema
  "
  [form-schema]
  (-> (s/schema->map form-schema)
      (assoc :form/fields (s/schema-keys form-schema)
             :form/schema form-schema)))

(defn values-from-signals
  "Get the form field values from a signals map"
  ([form-schema signals]
   (let [{form-key :form/key form-fields :form/fields} (form-options form-schema)]
     (values-from-signals form-fields form-key signals)))
  ([fields form-key signals]
   (-> signals form-key (select-keys fields))))

(defn validate-form-signals
  "Validate the form `signals` against the `form-schema`, returns the malli result"
  [form-schema signals]
  (->> (values-from-signals form-schema signals)
       (s/explain form-schema)
       (s/humanize)))

(defn validate-form
  "Validates the `signals` values against the `form-schema`, returning an error effect.

  Error effect is a :effect/kind :d*/merge-signals with the error message values.

  If `clear?` is true and there are no errors, then this function will return an effect that clears out existing errors.

  If `clear?` is false and there are no errors, then this function will return nil."
  ([form-schema signals]
   (validate-form form-schema signals nil))
  ([form-schema signals & {:keys [clear?] :or {clear? true}}]
   (let [{form-key :form/key} (form-options form-schema)
         validation-result    (validate-form-signals form-schema signals)
         error                (remove-untouched-errors form-key signals validation-result)]
     (if error
       (merge-errors signals form-key error {:only :touched})
       (when clear?
         (clear-form-errors signals form-key))))))
