;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.datastar.multicast
  (:require
   [hifi.datastar.spec :as spec]
   [promesa.exec.csp :as sp]))

(defonce ^:private !refresh-ch (atom nil))

(defn rerender-all!
  [event]
  (when-let [<refresh-ch @!refresh-ch]
    (sp/put <refresh-ch (or event []))))

(defn throttle [<in-ch msec]
  (let [;; No buffer on the out-ch as the in-ch should be buffered
        <out-ch (sp/chan)]
    (sp/go-loop []
      (when-some [event (sp/take! <in-ch)]
        (sp/put! <out-ch event)
        (Thread/sleep ^long msec)
        (recur)))
    <out-ch))

(defn stop-render-multicaster [instance]
  (let [<refresh-ch  (spec/<refresh-ch instance)
        refresh-mult (spec/refresh-mult instance)]
    (when <refresh-ch
      (sp/close! <refresh-ch)
      (reset! !refresh-ch nil))
    (when refresh-mult
      (sp/close! refresh-mult))
    (when <refresh-ch
      (sp/close! <refresh-ch)
      (reset! !refresh-ch nil))
    (when refresh-mult
      (sp/close! refresh-mult))))

(defn start-render-multicaster [{:keys [max-refresh-ms on-refresh]
                                 :or   {max-refresh-ms 100}}]
  (let [<refresh-ch  (sp/chan :buf (sp/dropping-buffer 1))
        _            (reset! !refresh-ch <refresh-ch)
        refresh-mult (-> (throttle <refresh-ch max-refresh-ms)
                         (sp/pipe
                          (sp/chan :buf (sp/dropping-buffer 1)
                                   :xf
                                   (map (fn [event]
                                          (when (and on-refresh (seq event))
                                            (on-refresh event))
                                          event))))
                         sp/mult*)]
    {spec/<refresh-ch  <refresh-ch
     spec/refresh-mult refresh-mult}))

(comment
  (def _s (start-render-multicaster {:max-refresh-ms 100}))
  (stop-render-multicaster _s)
  ;; rcf
  )
