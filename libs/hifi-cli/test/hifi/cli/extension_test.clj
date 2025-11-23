(ns hifi.cli.extension-test
  (:require
   [clojure.test :refer [deftest is]]
   [hifi.cli.extension :as ext]))

(defn secrets-tree
  []
  {:spec {:config-file {:default "config/hifi.edn"}}
   "secrets" {:spec {:profile {:default :dev}}
              "new" {:spec {:name {:default "secrets.dev.sops.edn"}
                            :overwrite {:coerce :boolean :default false}}
                     :fn (fn [_])}}})

(deftest dispatch-tree-prefers-explicit-values
  (let [res (ext/dispatch-tree (secrets-tree)
                               ["secrets" "new" "--name" "omg.secrets.sops.edn"]
                               {:bin-name "hifi"})]
    (is (= "omg.secrets.sops.edn" (get-in res [:opts :name])))))

(deftest dispatch-tree-keeps-default-when-no-explicit-value
  (let [res (ext/dispatch-tree (secrets-tree)
                               ["secrets" "new"]
                               {:bin-name "hifi"})]
    (is (= "secrets.dev.sops.edn" (get-in res [:opts :name])))))
