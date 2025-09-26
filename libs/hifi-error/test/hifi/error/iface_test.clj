(ns hifi.error.iface-test
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require
   [hifi.anomalies :as anom]
   [clojure.test :as test :refer [deftest is testing]]
   [malli.transform :as mt]
   [hifi.error.iface :as error]))

(defmacro test-validation [try-body catch-body]
  `(try
     ~try-body
     (is false "should not be reached")
     (catch clojure.lang.ExceptionInfo ~'e
       (let [~'msg  (ex-message ~'e)
             ~'data (dissoc (ex-data ~'e) :explanation)]
         ~catch-body))))

(deftest test-validate
  (testing "ex-data contains expected contents"
    (test-validation
     (error/validate! :string 1)
     (do
       (is (= (str :hifi.error.iface/schema-validation-error) msg))
       (is (= {:hifi.error.iface/id :hifi.error.iface/schema-validation-error
               ::anom/category      ::anom/incorrect
               :hifi.error.iface/url
               "https://hifi/errors/#:hifi.error.iface_schema-validation-error"
               :explanation-human   {:schema "schema is missing :name" :problems ["should be a string"]}}
              data)))))

  (testing "ex-data is affected by more-ex-data"
    (test-validation
     (error/validate! :string 1 {:more-ex-data {:foo :bar}})
     (is (= {:hifi.error.iface/id :hifi.error.iface/schema-validation-error
             ::anom/category      ::anom/incorrect
             :hifi.error.iface/url
             "https://hifi/errors/#:hifi.error.iface_schema-validation-error"
             :explanation-human   {:schema "schema is missing :name" :problems ["should be a string"]}
             :foo                 :bar}
            data))))
  (testing "error-msg is affected by error-msg"
    (test-validation
     (error/validate! :string 1 {:error-msg "foobar"})
     (is (= "foobar" msg)))))

(def schema [:map {:name ::test-schema}
             [:id :string]
             [:age {:optional true} [:int {:default 25}]]
             [:email {:optional true} :string]
             [:name :string]])

(deftest test-decode
  (testing "happy path decodes with defaults"
    (is (= {:id "123" :age 25 :name "alice"}
           (error/coerce! schema {:id "123" :name "alice"}))))
  (testing ":transformer affects output"
    (is (= {:id "123" :name "alice"}
           (error/coerce! schema {:id "123" :name "alice"} {:transformer (mt/default-value-transformer {::mt/add-optional-keys false})}))))
  (testing "on error ex-data contains expected data"
    (test-validation
     (error/coerce! schema {:id "123"})
     (is (= {:explanation-human   {:schema   ::test-schema
                                   :problems {:name ["should be a string"]}}
             ::anom/category      ::anom/incorrect
             :hifi.error.iface/url
             "https://hifi/errors/#:hifi.error.iface_schema-validation-error"
             :hifi.error.iface/id :hifi.error.iface/schema-validation-error}
            data)))))
