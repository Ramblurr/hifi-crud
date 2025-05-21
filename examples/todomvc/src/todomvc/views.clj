;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns todomvc.views
  (:require
   [flatland.ordered.map :refer [ordered-map]]
   [starfederation.datastar.clojure.expressions :refer [->expr]]
   [hifi.datastar :as datastar]))

(defn edit-view [{:keys [edit/editing-item-index edit/keyup-code]} index item]
  (when (and (= index editing-item-index)
             (not= "Escape" keyup-code))
    [:div [:input.edit (ordered-map :value                               (:item/title item)
                                    :data-signals-editaborted__ifmissing "false"
                                    :data-ref                            "editinput"
                                    :data-on-load                        "$editinput.focus()"
                                    :data-bind                           "edit"
                                    :data-on-blur                        (->expr (when (not $editaborted) (@post ("`/edit-action?action=Blur`"))))
                                    :data-on-keydown                     (->expr (when (or (= evt.key "Escape") (= evt.key "Enter"))
                                                                                   (set! $editaborted true)
                                                                                   (@post ("`/edit-action?action=${evt.key}`")))))]]))

(defn- item-visible? [item item-filter]
  (or (= :filter/all item-filter)
      (and (= :filter/active item-filter)
           (not (:item/completed? item)))
      (and (= :filter/completed item-filter)
           (:item/completed? item))))

(defn- item-view [{:keys [edit/editing-item-index app/item-filter] :as state} index item]
  (let [id (:item/id item)]
    (when (item-visible? item item-filter)
      [:li.box {:id                      (str "item" id)
                :data-signals__ifmissing (datastar/edn->json {(str "_transit" id) true})
                :data-class              (->expr {"transitioning" ($_transit ~id)})
                :data-on-load            (->expr
                                           (js/setTimeout #(set! ($_transit ~id) false) 100))
                :class                   (str "  " (cond
                                                     (= index editing-item-index) "editing"
                                                     (:item/completed? item)      "completed"))
                :data-on-dblclick        (->expr
                                           (when (not ($_transit ~id))
                                             (@post ~(str "/start-edit?index=" index))))}
       [:div.view
        [:input.toggle
         {:type           :checkbox
          :checked        (:item/completed? item)
          :value          index
          :data-on-change (->expr (@post ("`/toggle-complete?index=${evt.srcElement.value}`")))}]
        [:label (:item/title item)]
        [:button.destroy {:value               index
                          :data-on-click__stop (->expr
                                                 (set! ($_transit ~id) true)
                                                 (js/setTimeout #(@post ("`/destroy-todo?index=${evt.srcElement.value}`")) 250))}]]

       (edit-view state index item)])))

(defn- todo-list-view [{:keys [app/todo-items] :as state}]
  [:ul.todo-list
   (map-indexed (partial item-view state)
                todo-items)])

(defn filter-click [filter]
  (str
   "const url = new URL(window.location.href);"
   (if (not= "all" filter)
     (format "url.searchParams.set('filter', '%s');" filter)
     "url.searchParams.delete('filter');")
   "window.history.pushState({}, '', url); "
   (format "@post('/change-filter?filter=%s');" filter)
   "evt.srcElement.blur();"))

(defn- items-footer-view [{:keys [app/todo-items app/item-filter]}]
  (let [active-count (count (remove :item/completed? todo-items))]
    [:footer.footer
     [:span.todo-count
      [:strong active-count]
      (if (= active-count 1)
        " item"
        " items")
      " left"]
     [:ul.filters
      [:li [:a {:class                  (when (= :filter/all item-filter) "selected")
                :data-on-click__prevent (filter-click "all")
                :href                   "/"} "All"]]
      [:li [:a {:class                  (when (= :filter/active item-filter) "selected")
                :data-on-click__prevent (filter-click "active")
                :href                   "/filter=active"} "Active"]]
      [:li [:a {:class                  (when (= :filter/completed item-filter) "selected")
                :data-on-click__prevent (filter-click "completed")
                :href                   "/filter=completed"} "Completed"]]]
     (when (seq (filter :item/completed? todo-items))
       [:button.clear-completed {:data-on-click (->expr (@post "/clear-completed"))}
        "Clear completed"])]))

(defn- main-view [state]
  [:div.main
   [:input#toggle-all.toggle-all
    {:type          :checkbox
     :checked       (:app/mark-all-checkbox-checked? state)
     :data-on-click (->expr (@post "/toggle-all"))}]
   [:label {:for "toggle-all"}
    "Mark all as complete"]
   (todo-list-view state)])

(defn add-view [_]
  [:input.new-todo (ordered-map :type            :text
                                :autofocus       true
                                :enterkeyhint    "enter"
                                :placeholder     "What needs to be done?"
                                :data-bind-input ""
                                :data-on-keydown (->expr
                                                   (when (and (= evt.key "Enter")
                                                              (.-length (.trim $input)))
                                                     (@post "/add-todo")
                                                     (set! $input ""))))])

(def clojure-logo [:svg  {:xmlns "http://www.w3.org/2000/svg", :viewBox "0 0 256 256"
                          :style "height: 1.5em; width: 1.5em; vertical-align: middle; display: inline-block;"}
                   [:path
                    {:d
                     "M127.999 0C57.423 0 0 57.423 0 128.001c0 70.584 57.423 128.004 127.999 128.004 70.578 0 128.001-57.42 128.001-128.004C256 57.423 198.577 0 127.999 0",
                     :style {:fill "#fff"}}]
                   [:path
                    {:d
                     "M123.318 130.303a534.748 534.748 0 0 0-3.733 8.272c-4.645 10.524-9.789 23.33-11.668 31.534-.675 2.922-1.093 6.543-1.085 10.558 0 1.588.085 3.257.22 4.957a61.266 61.266 0 0 0 21.067 3.753 61.374 61.374 0 0 0 19.284-3.143c-1.425-1.303-2.785-2.692-4.023-4.257-8.22-10.482-12.806-25.844-20.062-51.674M92.97 78.225c-15.699 11.064-25.972 29.312-26.011 49.992.039 20.371 10.003 38.383 25.307 49.493 3.754-15.637 13.164-29.955 27.275-58.655a230.831 230.831 0 0 0-2.862-7.469c-3.909-9.806-9.551-21.194-14.586-26.351-2.567-2.694-5.682-5.022-9.123-7.01",
                     :style {:fill "#91dc47"}}]
                   [:path
                    {:d
                     "M181.394 198.367c-8.1-1.015-14.785-2.24-20.633-4.303a73.181 73.181 0 0 1-32.642 7.643c-40.584 0-73.483-32.894-73.488-73.49 0-22.027 9.704-41.773 25.056-55.24-4.106-.992-8.388-1.571-12.762-1.563-21.562.203-44.323 12.136-53.799 44.363-.886 4.691-.675 8.238-.675 12.442 0 63.885 51.791 115.676 115.671 115.676 39.122 0 73.682-19.439 94.611-49.169-11.32 2.821-22.206 4.17-31.528 4.199-3.494 0-6.774-.187-9.811-.558",
                     :style {:fill "#63b132"}}]
                   [:path
                    {:d
                     "M159.658 175.953c.714.354 2.333.932 4.586 1.571 15.157-11.127 25.007-29.05 25.046-49.307h-.006c-.057-33.771-27.386-61.096-61.165-61.163a61.312 61.312 0 0 0-19.203 3.122c12.419 14.156 18.391 34.386 24.168 56.515.003.01.008.018.01.026.011.018 1.848 6.145 5.002 14.274 3.132 8.118 7.594 18.168 12.46 25.492 3.195 4.908 6.709 8.435 9.102 9.47",
                     :style {:fill "#90b4fe"}}]
                   [:path
                    {:d
                     "M128.122 12.541c-38.744 0-73.016 19.073-94.008 48.318 10.925-6.842 22.08-9.31 31.815-9.222 13.446.039 24.017 4.208 29.089 7.06a53.275 53.275 0 0 1 3.527 2.247 73.183 73.183 0 0 1 29.574-6.215c40.589.005 73.493 32.899 73.499 73.488h-.006c0 20.464-8.37 38.967-21.863 52.291 3.312.371 6.844.602 10.451.584 12.811.006 26.658-2.821 37.039-11.552 6.769-5.702 12.44-14.051 15.585-26.569.615-4.835.969-9.75.969-14.752 0-63.882-51.786-115.678-115.671-115.678",
                     :style {:fill "#5881d8"}}]])

(defn app-view [{:keys [app/todo-items] :as state}]
  [:div {:data-on-popstate__window (->expr (@post ("`/change-filter?filter=${new URL(window.location.href).searchParams.get('filter') || 'all'}`")))}
   [:section.todoapp
    [:header.header
     [:h1 "todos"] (add-view state)]
    (when (seq todo-items)
      (list (main-view state)
            (items-footer-view state)))]
   [:footer.info
    [:p "Double-click to edit a todo"]
    [:p "Built with ❤️ using "
     [:a {:href "https://clojure.org/"} "Clojure"
      clojure-logo]
     " and "
     [:a {:href "https://data-star.dev"} "Datastar"
      "🚀"]]
    [:p "Source code can be found "
     [:a {:href "https://github.com/ramblurr/hifi-crud/blob/master/examples/todomvc/src/todomvc/app.clj"} "here"]]
    [:p "Part of "
     [:a {:href "https://todomvc.com"} "TodoMVC"]]]
   ;; [:pre {:data-json-signals true}]
   ])

(datastar/rerender-all!)
