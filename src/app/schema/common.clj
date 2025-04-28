;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT


(ns app.schema.common
  (:require
   [exoscale.cloak :as cloak]
   [clojure.string :as string]
   [malli.core :as m]
   [malli.transform :as mt]))

(def EmailAddress (m/-simple-schema {:type            :email-address
                                     :pred            #(and (string? %)
                                                            (re-matches #"^.+\@.+\..+$" %))
                                     :type-properties {:title              "email"
                                                       :description        "string with valid email address"
                                                       :error/message      "should be a valid email address"
                                                       :decode/string      string/lower-case
                                                       :encode/string      string/lower-case
                                                       :decode/json        string/lower-case
                                                       :encode/json        string/lower-case
                                                       :json-schema/type   "string"
                                                       :json-schema/format "string"}}))
(def NonBlankString
  (m/-simple-schema {:type            :app.malli/non-blank-string
                     :pred            #(and (string? %) (not (string/blank? %)))
                     :type-properties {:error/message      "should not be blank"
                                       :decode/string      str
                                       :encode/string      str
                                       :decode/json        str
                                       :encode/json        str
                                       :json-schema/type   "string"
                                       :json-schema/format "string"}}))

(def Secret
  [:fn cloak/secret?])

(def registry
  {:app.malli/email-address    EmailAddress
   :app.malli/non-blank-string NonBlankString
   :app.malli/secret           Secret})
