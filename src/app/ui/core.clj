(ns app.ui.core
  (:require
   [app.util.error :as u.error]
   [clojure.string :as str]
   [dev.onionpancakes.chassis.core]
   [flatland.ordered.map :refer [ordered-map]]
   [malli.core :as m]
   [malli.error :as me]
   [malli.experimental.lite :as l]))

(defn cs [& names]
  (str/join " " (filter identity names)))

(defn raw? [v]
  (= dev.onionpancakes.chassis.core.RawString
     (type v)))

(defonce ^:dynamic *validate-opts* false)

(defmacro attr-map
  "Creates an ordered map type hinted with IPersistentMap for use with chassis

  Usage: (attr-map :id :my-thing :class \"btn\")"
  [& args]
  `^clojure.lang.IPersistentMap (ordered-map ~@args))

(defn wrap-text-node
  ([children]
   (wrap-text-node :span children))
  ([tag children]
   (if (or (raw? (first children)) (and (string? (first children)) (not= \< (first children))))
     [tag children]
     children)))

(defn bad-opt-value-callout

  [{:keys [point-of-interest-opts callout-opts]}]
  (let [message      ((requiring-resolve 'bling.core/point-of-interest) point-of-interest-opts)
        callout-opts (merge callout-opts {:padding-top 1})]
    (tap> (with-meta  [(str (:label callout-opts) "\n" message)] {:ansi/color true}))
    ((requiring-resolve 'bling.core/callout) callout-opts message)))

(defn warning-header
  [{:keys [component opt value]}]
  (apply (requiring-resolve 'bling.core/bling)
         (concat
          [[:italic "component: "] [:bold (:name component)]
           "\n\n"
           [:italic "option:    "] [:bold opt]
           "\n\n"
           [:italic "invalid:   "] [:bold (if (nil? value)
                                            "nil"
                                            value)]])))

(defn strip-should-be [msg]
  (if (re-find #"^(?i)should be" msg)
    (subs msg 10)
    msg))

(defn warning-body
  [{:keys [opt msg trace]}]
  (let [w (java.io.StringWriter.)]
    (u.error/print-trace trace w)
    (str
     ((requiring-resolve 'bling.core/bling)
      "Value for the attribute "
      [:bold opt]
      " "
      msg
      "\n\n"
      [:italic "Compacted stacktrace"] "\n")
     w)))

(defn enable-opts-validation! []
  (alter-var-root #'*validate-opts* (constantly true)))

(defn disable-opts-validation! []
  (alter-var-root #'*validate-opts* (constantly false)))

(defn error->bling-opts [trace component explain]
  (map (fn [[k error]]
         (let [opt k]
           {:point-of-interest-opts {:header (warning-header {:opt       opt
                                                              :value     (get-in explain [:value k])
                                                              :component component})

                                     :body (warning-body {:opt   opt
                                                          :trace trace
                                                          :msg   (str/join "; " error)})}

            :callout-opts {:type  :warning
                           :label "WARNING​ Invalid ui attribute"}}))

       (me/humanize explain
                    {:resolve me/-resolve-root-error
                     :errors  (-> me/default-errors
                                  (assoc ::m/missing-key {:error/fn (fn [_ _]
                                                                      "is missing")}))})))

(defn stack-trace []
  (->> (.getStackTrace (Thread/currentThread))
       (u.error/clean-trace)
       (drop 2)
       (reverse)))

(defn validate-opts [schema opts]
  (let [s (if (map? schema)
            (l/schema schema)
            schema)]
    (when-let [problem (m/explain s opts)]
      (let [trace (stack-trace)]
        (doseq [bling-opts (error->bling-opts trace (meta schema) problem)]
          (bad-opt-value-callout bling-opts))))))

(defn validate-opts! [schema attrs]
  (when *validate-opts*
    (validate-opts schema attrs)))

(defn merge-attrs*
  ^clojure.lang.IPersistentMap [orig-map & {:as extra}]
  (reduce (fn [acc [k v]]
            (case k
              :class (update acc :class #(str v " " %))
              (assoc acc k v))) orig-map extra))

(defmacro merge-attrs
  "Intelligently merges HTML attributes returning a map that is unamiguous to chassis.

  Intelligent means:
    - :class strings are concatenated
    - everything else is assoced like a normal merge
  "
  [& args]
  `^clojure.lang.IPersistentMap (merge-attrs* ~@args))

(defn norm
  "Normalizes the hiccup element to a vector of [tag attrs & children]"
  [hiccup]
  (let [[tag & [attrs & _ch :as children]] hiccup]
    (if (map? attrs)
      hiccup
      [tag nil children])))

(defn assoc-attr
  "Assoc attributes to the hiccup element"
  [hiccup & {:as args}]
  (update-in (norm hiccup) [1]
             merge-attrs*
             args))

(assoc-attr [:input "Coolbeans"] :class "mt-3" :type :text)
(assoc-attr [:input {:placeholder "Legume"} "Coolbeans"] :class "mt-3" :type :text)
(assoc-attr [:input {:type :datetime} "Coolbeans"] :class "mt-3" :type :text)

(defn add-class
  "Appends a class to the hiccup element"
  [hiccup cls]
  (update-in (norm hiccup) [1 :class]
             #(cs % cls)))

(defn dispatch [cmd]
  (assert (keyword? cmd))
  (str "@post(\"/cmd?cmd=" (subs (pr-str cmd) 1) "\")"))
