(ns hifi.html
  (:refer-clojure :exclude [compile])
  (:require
   [medley.core :as medley]
   [hifi.util.codec :as codec]
   [dev.onionpancakes.chassis.core :as chassis]
   [dev.onionpancakes.chassis.compiler :as cc]))

(cc/set-warn-on-ambig-attrs!)

(def ->str
  "Returns HTML string given a HTML Node tree."
  chassis/html)

(defmacro compile
  "Compiles the node form, returning a compacted equivalent form.
  The return value may be a content vector or an unwrapped value
  if fewer than two forms are returned."
  [& hiccups]
  (let [node (vec hiccups)]
    `(cc/compile ~node)))

(def raw
  "Wraps value as an unescaped string that will be rendered directly to HTML.

  WARNING: This bypasses HTML escaping and can lead to XSS vulnerabilities if used with untrusted input.

  Examples:
  (raw \"<br>\")   ;; safe: known markup
  (raw user-input) ;; dangerous: could contain malicious scripts

  Arguments:
  `value` - String or value to be rendered without escaping
  `more` - Additional values to be concatenated"
  chassis/raw-string)

(def doctype-html5 chassis/doctype-html5)

(defmacro script
  "Emits a [:script] tag for an [[hifi.util.assets/static-asset]].

  Expects `:!asset` key in the map of attributes, all other vals will be merged into the scrip tag's attributes."
  [attrs]
  (let [asset-sym   (:!asset attrs)
        other-attrs (dissoc attrs :!asset)]
    `[:script (merge {:src       (:href @~asset-sym)
                      :integrity (:integrity @~asset-sym)}
                     ~other-attrs)]))

(defmacro stylesheet
  "Emits a [:link {:rel \"stylesheet\"}] tag for an [[hifi.util.assets/static-asset]].

  Expects `:!asset` key in the map of attributes, all other vals will be merged into the link tag's attributes."
  [attrs]
  (let [asset-sym   (:!asset attrs)
        other-attrs (dissoc attrs :!asset)]
    `[:link (merge {:rel       "stylesheet"
                    :type      "text/css"
                    :href      (:href @~asset-sym)
                    :integrity (:integrity @~asset-sym)}
                   ~other-attrs)]))

(defmacro icon
  "Emits a [:link {:rel \"icon\"}] tag for an [[hifi.util.assets/static-asset]].

  Expects `:!asset` key in the map of attributes, all other vals will be merged into the link tag's attributes."
  [attrs]
  (let [asset-sym   (:!asset attrs)
        other-attrs (dissoc attrs :!asset)]
    `[:link (merge {:rel  "icon"
                    :href (:href @~asset-sym)}
                   ~other-attrs)]))

(defn csrf-token-input
  "Returns hiccup for <input type=\"hidden\" /> field with the anti-forgery token name and value"
  [req]
  [:input {:type "hidden" :name "__anti-forgery-token" :value (:pink.interceptors.csrf/token req)}])

(def csrf-cookie-js-prod
  "document.cookie.match(/(^| )__Host-csrf=([^;]+)/)?.[2]")

(def csrf-cookie-js-dev
  "document.cookie.match(/(^| )csrf=([^;]+)/)?.[2]")

(def nbsp [:span chassis/nbsp])
(def emdash [:span (raw "&mdash;")])
(def endash [:span (raw "&ndash;")])
(def ellipsis [:span (raw "&hellip;")])
(def euro [:span (raw "&euro;")])
(def cent [:span (raw "&cent;")])
(def copyright [:span (raw "&copy;")])
(def trademark [:span (raw "&reg;")])
(def dagger [:span (raw "&#8224;")])
(def Dagger [:span (raw "&#8225;")])
(def prime [:span (raw "&#8242;")])
(def Prime [:span (raw "&#8243;")])
(def almost [:span (raw "&asymp;")])
(def half [:span (raw "&frac12;")])
(def degree [:span (raw "&deg;")])
(def plusminus [:span (raw "&plusmn;")])

(defn html-document
  "A standard HTML5 document template. If it doesn't suit you, just make your own.

  Returns a complete HTML5 document as Hiccup data.

  Includes metadata, social media tags, font loading, and favicons, targeting modern browsers.

  The first argument is the options map, the remaining arguments are placed inside the <body>.

  Options map:
    `lang` - HTML language attribute (defaults to \"en\")
    `title` - Page title
    `description` - Meta description
    `favicon` - Path to favicon.ico
    `svg-icon` - Path to SVG favicon
    `apple-touch-icon` - Path to 180x180px Apple touch icon
    `url` - url for og:url
    `canonical` - Canonical URL <link ref=canonical>
    `head` - Additional Hiccup elements to include in head (e.g., (list [:link ..] [:script ...])
    `body-attrs` - Map of HTML attributes for body tag

  All of the options are optional.
  "
  [{:keys [lang title description  favicon svg-icon apple-touch-icon canonical head body-attrs]} & body]
  (cc/compile
   [chassis/doctype-html5
    [:html {:lang (or lang "en")}
     [:head
      [:title {} title]
      [:meta {:charset "UTF-8"}]
      head
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
      (when description
        [:meta {:name "description" :content description}])
      (when canonical
        [:link {:ref "canonical" :href canonical}])
      (when favicon
        ;; 32x32 is there to prevent a chrome bug where it chooses an ico over an svg
        [:link {:rel "icon" :href favicon :sizes "32x32"}])
      (when svg-icon
        [:link {:rel "icon" :href svg-icon :type "image/svg+xml"}])
      (when apple-touch-icon
        ;;  should be "180×180", at least in 2024
        [:link {:rel "apple-touch-icon" :href apple-touch-icon}])]
     [:body ^clojure.lang.IPersistentMap body-attrs
      body]]]))

(def default-on-load-js
  ;; Quirk with browsers is that cache settings are per URL not per
  ;; URL + METHOD this means that GET and POST cache headers can
  ;; mess with each other. To get around this an unused query param
  ;; is added to the url.
  "@post(window.location.pathname + (window.location.search + '&u=').replace(/^&/,'?'))")

(def default-tab-id-js "self.crypto.randomUUID()")

(defn shim-document [{:keys [body-pre body-post
                             csrf-cookie-js
                             tab-id-js
                             on-load-js]
                      :or   {on-load-js     default-on-load-js
                             tab-id-js      default-tab-id-js
                             csrf-cookie-js csrf-cookie-js-prod}
                      :as   opts}]
  (html-document opts
                 (list
                  [:div {:data-signals-csrf               csrf-cookie-js
                         :data-signals-tab-id__case.kebab tab-id-js}]
                  [:div {:data-on-load on-load-js}]
                  [:noscript "Your browser does not support JavaScript!"]
                  body-pre
                  [:main#morph]
                  body-post)))

(defn shim-page-resp [{:keys [compress-fn encoding body]
                       :or   {compress-fn identity}}]
  (let [body (->str body)]
    (-> {:status  200
         :headers (-> {"Content-Type"  "text/html"
                       "Cache-Control" "no-cache, must-revalidate"}
                      (medley/assoc-some "Content-Encoding" encoding))
         :body    (compress-fn body)}
        ;; Etags ensure the shim is only sent again if it's contents have changed
        (assoc-in [:headers "ETag"] (codec/digest body)))))

(defn shim-handler [resp]
  (let [etag (get-in resp [:headers "ETag"])]
    (fn handler [req]
      (if (= (get-in req [:headers "if-none-match"]) etag)
        {:status 304}
        resp))))
