;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT


(ns dev
  (:require

   [hyperlith.core :as h]
   [hyperlith.extras.datahike :as d]
   [app.schema :as schema]
   [portal-helpers :as portal-repl]
   [app.main :as app]
   [app.ui.core :as uic]
   [portal.colors]
   [portal.api :as p]))

;; --------------------------------------------------------------------------------------------
;; Portal & Logging

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
;; (p/open {:theme :portal.colors/gruvbox})
;; (add-tap portal.api/submit)
;; (remove-tap portal.api/submit)
(uic/enable-opts-validation!)

(defn reset []
  (app/stop)
  (app/start))

(comment

  (set! *print-namespace-maps* false)
  (clojure.repl.deps/sync-deps)
  ;;
  )
(comment
  (do
    (require '[clojure.java.shell :as shell])

    (defn rm-db []
      (shell/sh "rm" "-rf" "db")
      (shell/sh "mkdir" "db"))

    (defn setup []
      (->  {}
           (d/ctx-start  "./db/dev1.sqlite")
           (schema/ctx-start)))

    (defn teardown [app]
      (d/ctx-stop app)
      (Thread/sleep 200))

    (defn reset [app]
      (when app
        (teardown app))
      (rm-db)
      (Thread/sleep 300))

    (defn tx-n [app n]
      (let [d (take n (read-string (slurp "extra/data.tx")))]
        @(d/tx! (:conn app)
                {:tx-data d})
        (tap> [:txed n])))

    (defn poke-tx [app]
      @(d/tx! (:conn app) {:tx-data
                           [{:session/id (str (random-uuid))}]}))

    (defn test [n]
      (reset nil)
      (let [app (setup)]
        (tap> [:test :n n])
        (tx-n app n)
        (teardown app)
        (let [app2 (setup)]
          (try
            (poke-tx app2)
            (reset app2)
            :ok
            (catch Exception e
              (reset app2)
              (tap> [:poke-failed :n n :ex e])
              :fail)))))

    (def total-tx (count (read-string (slurp "extra/data.tx"))))

    ;; (def max-iterations 1000)

    (def CONTINUE? (atom true)))

  (do
    (def result (promise))

    (def fut
      (future
        (deliver result
                 ;; Binary search  bisect
                 (loop [low  0 ;; Known good value
                        high total-tx
                        iter 0] ;; Known bad value
                   (tap> [:iteration :low low :high high])
                   (cond
                     (not @CONTINUE?)
                     {:result :abort :continue false}

                     ;; (>= iter max-iterations)
                     ;; {:result :abort :max-iterations iter}

                     (<= (- high low) 1)
                     {:result :fail :threshold high} ;; high is the smallest failing n

                     :else
                     (let [mid (quot (+ low high) 2)
                           r   (test mid)]
                       (if (= :ok r)
                         (recur mid high (inc iter)) ;; mid works, search higher
                         (recur low mid (inc iter)))) ;; mid fails, search lower
                     )))
        (tap> [:done :result @result]))))
  (future-cancel fut)
  (realized? result)
  @result
  ;;
  )
