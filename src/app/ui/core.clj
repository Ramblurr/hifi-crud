(ns app.ui.core
  (:require
   [dev.onionpancakes.chassis.core]
   [app.util.error :as u.error]
   [flatland.ordered.map :refer [ordered-map]]
   [malli.core :as m]
   [malli.experimental.lite :as l]
   [malli.error :as me]
   [clojure.string :as str]
   [clojure.set :as set]))

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

(defn fqns-sym
  [m]
  (symbol (str (:ns m)
               "/"
               (str/replace (name (:name m))
                            #"\*$" ""))))

(defn warning-header
  [{:keys [m opt value]}]
  (let [component                     (fqns-sym m)
        {:keys [_file _line _column]} m]
    (apply (requiring-resolve 'bling.core/bling)
           (concat
            [[:italic "component: "] [:bold component]
             "\n\n"
             [:italic "option:    "] [:bold opt]
             "\n\n"
             [:italic "invalid:   "] [:bold value]]))))

(defn strip-should-be [msg]
  (if (re-find #"^(?i)should be" msg)
    (subs msg 10)
    msg))

(defn warning-body
  [{:keys [opt msg trace]}]
  (let [short-trace (reverse (take 10 (drop 3 trace)))
        w           (java.io.StringWriter.)]
    (u.error/print-trace short-trace w)
    (str
     ((requiring-resolve 'bling.core/bling)
      "Value for the "
      [:bold opt]
      " "
      msg
      "\n\n"
      [:italic "Stacktrace preview:"] "\n")
     w)))

(defn enable-opts-validation! []
  (alter-var-root #'*validate-opts* (constantly true)))

(defn disable-opts-validation! []
  (alter-var-root #'*validate-opts* (constantly false)))

(defn error->bling-opts [trace cvar explain]
  (map (fn [[k error]]
         (let [opt (str ":-" (name k))]
           {:point-of-interest-opts {:header (warning-header {:opt   opt
                                                              :value (get-in explain [:value k])
                                                              :m     (meta cvar)})

                                     :body (warning-body {:opt   opt
                                                          :trace trace
                                                          :msg   (str/join "; " error)})}

            :callout-opts {:type  :warning
                           :label "WARNING​ Invalid option value"}}))

       (me/humanize explain)))

(defn stack-traces []
  (->> (.getStackTrace (Thread/currentThread))
       (map StackTraceElement->vec)
       (u.error/clean-trace)))

(defn validate-opts [cvar opts]
  (when *validate-opts*
    (when-let [opt-defs (:opts (meta cvar))]
      (let [s (l/schema opt-defs)]
        (when-let [problem (m/explain s opts)]
          (let [trace (stack-traces)]
            (doseq [bling-opts (error->bling-opts trace cvar problem)]
              (bad-opt-value-callout bling-opts)))

          #_(throw (ex-info (str "Invalid options passed to" cvar) {:error problem
                                                                    :human (me/humanize problem)})))))))

(defn warn-on-attr-collision [cvar attrs]
  (when *validate-opts*

    (when-let [opt-defs (:opts (meta cvar))]
      (doseq [k (set/intersection (set (keys opt-defs)) (set (keys attrs)))]
        (let [opt-k          (str ":-" (name k))
              component-name (fqns-sym (meta cvar))
              msg            (str component-name " called with html attribute " k " that is also an option, did you mean " opt-k " ?")]
          ((requiring-resolve 'bling.core/callout) {:type :warning} msg))))))

(defn attr+children [coll]
  (when (coll? coll)
    (let [[a & xs] coll
          attr     (when (map? a) a)]
      [attr (if attr xs coll)])))

(defn user-attr? [x]
  (and (keyword? x)
       (->> x name (re-find #"^-[^\s\d]+"))))

(defn unwrapped-children [children]
  (let [fc (nth children 0 nil)]
    (if (and
         (seq? children)
         (= 1 (count children))
         (seq? fc)
         (seq fc))
      fc
      children)))

(defn extract
  "Extracts component options from normal html attributes and children elements.

  Returns a vector of [options html-attributes children]"
  ;; it is important that we preserve the type of the attr map
  ;; in case the user has supplied an array-map because the order of their attributes is important
  [cvar args]
  (when (coll? args)
    (let [[attr* children] (attr+children args)
          user-ks          (some->> attr*
                                    keys
                                    (filter user-attr?)
                                    (into #{}))
          ;; Calling dissoc on an array map always yields an array map
          attr             (apply dissoc attr* user-ks)
          opts             (select-keys attr* (into [] user-ks))
          supplied-opts    (->> opts
                                (map (fn [[k v]]
                                       [(-> k name (subs 1) keyword) v]))
                                (into {}))]

      (when *validate-opts*
        (validate-opts cvar supplied-opts)
        (warn-on-attr-collision cvar attr))

      [supplied-opts
       attr
       (->> children
            (remove nil?)
            unwrapped-children)])))

(defn merge-attrs [orig-map & {:as extra}]
  (reduce (fn [acc [k v]]
            (case k
              :class (update acc :class #(str v " " %))
              (assoc acc k v))) orig-map extra))

(defn easy-extract [comp args class]
  (let [[opts attrs children] (extract comp args)]
    [opts
     (merge-attrs attrs :class class) children]))

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
             merge-attrs
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
