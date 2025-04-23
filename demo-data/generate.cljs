(ns retail-data-generator
  (:require ["data-generator-retail$default" :as generator]
            ["fs" :as fs]))

(let [gen-data (generator/default)
      commands (.-commands gen-data)
      fixed-data #js {:orders commands}]

  (doseq [k (js/Object.keys gen-data)]
    (when-not (= k "commands")
      (aset fixed-data k (aget gen-data k))))

  (println (js/JSON.stringify fixed-data)))

