;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns todomvc.commands
  (:require [cuerdas.core :as str]
            [starfederation.datastar.clojure.api :as d*]
            [medley.core :as medley]
            [hifi.datastar.tab-state :as tab-state]
            [hifi.datastar.http-kit :as d*http-kit]
            [hifi.datastar :as datastar]))

;; this validation is only so we can't fill up the server's memory easily
;; it should also be combined with rate-limiting in the reverse proxy
(defn- validate-input [input]
  (let [i (str/trim input)]
    (if (> (count i) 40)
      (throw (ex-info "Input too long" {}))
      i)))

(defn- maybe-add [coll s]
  (let [trimmed (validate-input s)]
    (if (str/blank? trimmed)
      coll
      (conj coll {:item/title      trimmed
                  :item/completed? false
                  :item/id         (random-uuid)}))))

(defn- mark-items-as [items completed?]
  (mapv (fn [item]
          (assoc item :item/completed? completed?))
        items))
(defn toggle-all [state _]
  (-> state
      (update :app/mark-all-checkbox-checked? not)
      (update :app/todo-items mark-items-as (not (:app/mark-all-checkbox-checked? state)))))

(defn toggle-complete [state {:keys [index]}]
  (update-in state [:app/todo-items (parse-long index) :item/completed?] not))

(defn clear-completed [state _]
  (update state :app/todo-items (partial filterv (complement :item/completed?))))

(defn add-todo [state {:keys [input]}]
  (-> state
      (update :app/todo-items maybe-add input)))

(defn destroy-todo [state {:keys [index]}]
  (update state :app/todo-items #(into [] (medley/remove-nth (parse-long index) %))))

(defn start-edit [state {:keys [index]}]
  (let [index (parse-long index)]
    (assoc state
           :edit/editing-item-index index
           :d*/signals {:edit        (get-in state [:app/todo-items index :item/title])
                        :editaborted false})))

(defn edit-action [state {:keys [edit action]}]
  (if (:edit/editing-item-index state)
    (condp some [action]
      #{"Enter" "Blur"} (-> state
                            (dissoc :edit/editing-item-index)
                            (assoc :d*/signals {:edit nil :editaborted false})
                            (assoc-in [:app/todo-items (:edit/editing-item-index state) :item/title]
                                      (validate-input edit)))
      #{"Escape"}       (-> state
                            (assoc :d*/signals {:edit nil :editaborted false})
                            (dissoc :edit/editing-item-index))
      state)
    state))

(defn change-filter [state {:keys [filter]}]
  (assoc state :app/item-filter (get {"active"    :filter/active
                                      "completed" :filter/completed
                                      "all"       :filter/all} filter :filter/all)))

(defn cmd [f]
  {:post {:handler (d*http-kit/action-handler-async
                    (fn cmd-handler* [req sse-gen]
                      (let [signals      (::datastar/signals req)
                            query-params (medley/map-keys str/keyword (:query-params req))]
                        (tab-state/transact! req (fn cmd-handler*transact [state]
                                                   (-> state
                                                       (dissoc :d*/signals)
                                                       (f (merge signals query-params)))))
                        (when-let [signals (:d*/signals (tab-state/tab-state! req))]
                          (d*/patch-signals! sse-gen (datastar/edn->json signals)))
                        (d*/close-sse! sse-gen)
                        nil)))}})

(def commands
  [""
   ["/change-filter" (cmd change-filter)]
   ["/edit-action" (cmd edit-action)]
   ["/start-edit" (cmd start-edit)]
   ["/toggle-all" (cmd toggle-all)]
   ["/toggle-complete" (cmd toggle-complete)]
   ["/add-todo" (cmd add-todo)]
   ["/destroy-todo" (cmd destroy-todo)]
   ["/clear-completed" (cmd clear-completed)]])
