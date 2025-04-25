(ns app.shim
  (:require
   [hyperlith.impl.brotli :as br]
   [hyperlith.impl.crypto :as crypto]
   [hyperlith.impl.datastar :as d*]
   [hyperlith.impl.headers :refer [default-headers]]
   [hyperlith.impl.html :as h]
   [hyperlith.impl.session :refer [csrf-cookie-js]]))

;; HACK(hyperlith): I wish hyperlith let us do this without importing impl nses

(defn build-shim-page-resp [head-hiccup body-pre body-post]
  (let [body (-> (h/html
                   [h/doctype-html5
                    [:html  {:lang                   "en"
                             :data-signals-_darkmode "window.matchMedia(\"(prefers-color-scheme: dark)\").matches"
                             :data-persist           "_darkmode"
                             :data-class-dark        "$_darkmode"}
                     [:head
                      [:meta {:charset "UTF-8"}]
                      (when head-hiccup head-hiccup)
                      ;; Scripts
                      [:script#js {:defer true :type "module"
                                   :src   (d*/datastar :path)}]
                      ;; Enables responsiveness on mobile devices
                      [:meta {:name    "viewport"
                              :content "width=device-width, initial-scale=1.0"}]]
                     [:body
                      [:div {:data-signals-csrf csrf-cookie-js}]
                      [:div {:data-on-load d*/on-load-js}]
                      [:noscript "Your browser does not support JavaScript!"]
                      body-pre
                      [:main {:id "morph"}]
                      body-post]]])
                 h/html->str)]
    (-> {:status  200
         :headers (assoc default-headers "Content-Encoding" "br")
         :body    (-> body (br/compress :quality 11))}
        ;; Etags ensure the shim is only sent again if it's contents have changed
        (assoc-in [:headers "ETag"] (crypto/digest body)))))

(defn shim-handler [{:keys [head body-pre body-post]}]
  (let [resp (build-shim-page-resp head body-pre body-post)
        etag (get-in resp [:headers "ETag"])]
    (fn handler [req]
      (if (= (get-in req [:headers "if-none-match"]) etag)
        {:status 304}
        resp))))
