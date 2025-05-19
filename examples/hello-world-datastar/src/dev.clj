(ns dev
  {:clj-kondo/config '{:linters       {:unused-namespace     {:level :off}
                                       :unresolved-namespace {:level :off}
                                       :unused-referred-var  {:level :off}}
                       :skip-comments true}}
  (:require
   [portal-helpers :as portal-repl]
   [portal.colors]
   [portal.api :as p]))

;; (set! *warn-on-reflection* true)

(def transforms portal-repl/recommended-transforms)
(def tap-routing nil)

(defonce my-submit (portal-repl/make-submit :transforms #'transforms :tap-routing #'tap-routing))
(add-tap my-submit)
(comment
  (do
    (remove-tap my-submit)
    (def my-submit (portal-repl/make-submit :transforms #'transforms :tap-routing #'tap-routing))
    (add-tap my-submit)))

;; (portal.api/set-theme ::my-theme (merge (:portal.colors/gruvbox portal.colors/theme) {:font-size 8}))
(p/open {:theme :portal.colors/gruvbox})

(comment

  (clojure.repl.deps/sync-deps)

  (require
   '[dev.onionpancakes.chassis.compiler :as cc]
   '[dev.onionpancakes.chassis.core :as c])

  (defmethod c/resolve-alias
    ::sub-widget
    [_ attrs children]
    (cc/compile
     [:div (assoc attrs :id "subwidget")
      children]))

  (cc/compile
   [::widget {:class "widget"}
    [:h1 "Hello World"]
    [:p "This is a test."]])
  ;; => [#object[dev.onionpancakes.chassis.core.OpeningTag 0x39b7ee6b "<div id=\"widget\" class=\"widget\">"] [#object[dev.onionpancakes.chassis.core.OpeningTag 0x52b94afd "<div id=\"subwidget\">"] [[#object[dev.onionpancakes.chassis.core.RawString 0x2ae21726 "<h1>Hello World</h1><p>This is a test.</p>"]]] #object[dev.onionpancakes.chassis.core.RawString 0x7b0f07ca "</div>"]] #object[dev.onionpancakes.chassis.core.RawString 0x17265aae "</div>"]]

  ;; => [#object[dev.onionpancakes.chassis.core.OpeningTag 0x65e64a07 "<div id=\"widget\" class=\"widget\">"] [#object[dev.onionpancakes.chassis.core.RawString 0x4ab0232f "<h1>Hello World</h1><p>This is a test.</p>"]] #object[dev.onionpancakes.chassis.core.RawString 0x41695715 "</div>"]]

  ;;
  )

(require '[medley.core :as medley])
(let [lookup  {:debug-errors? [:middleware :opts :exception :debug-errors?]
               :middleware    [:middleware]
               :host          [:http-server :host]}
      opts    {:debug-errors? true
               :host          "127.0.0.1"
               :middleware    {:opts {:exception-backstop {:report "reporter"}}}}
      other1  {:foo :bar}
      other3  {:middleware {:another :thing}}
      desired {:middleware {:opts {:exception          {:debug-errors? true}
                                   :exception-backstop {:report "reporter"}}}
               :host       "127.0.0.1"}]

  (->> lookup
       (map (fn [[k path]]
              (assoc-in {} path (get opts k))))
       (apply medley/deep-merge other1 other3))
  #_(reduce
     (fn [result [k v]]
       (if-let [path (get lookup k)]
         (assoc-in result path v)
         result))
     {}
     opts))

(comment

  ;;
  )
