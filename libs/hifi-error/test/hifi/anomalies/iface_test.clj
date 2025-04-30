(ns hifi.anomalies.iface-test
  (:require [clojure.test :as test :refer [deftest is]]
            [malli.core :as m]
            [hifi.anomalies.iface :as anom]))

(deftest validates
  (is (m/validate anom/Anomaly {::anom/category ::anom/incorrect ::anom/message "foo"}))
  (is (not (m/validate anom/Anomaly {::anom/message "foo"}))) "missing category")
