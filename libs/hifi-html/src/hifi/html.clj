(ns hifi.html
  (:refer-clojure :exclude [compile])
  (:require
   [medley.core :as medley]
   [hifi.html.impl :as impl]
   [hifi.util.codec :as codec]
   [dev.onionpancakes.chassis.core :as chassis]
   [dev.onionpancakes.chassis.compiler :as cc]))

;; TODO move to dev-time only
(cc/set-warn-on-ambig-attrs!)

(defn ->str
  "Converts a Hiccup-style HTML node tree into an HTML string.

  This function processes asset-marked elements (stylesheet links, scripts, images)
  and resolves their paths through the asset pipeline before rendering.

  Arguments:
    ctx  - (optional) Context map containing:
           {:hifi/assets asset-resolver} - Asset resolver for path resolution
    root - Hiccup-style HTML data structure to render

  Returns:
    String of rendered HTML with asset paths resolved.

  Examples:
    ;; Simple rendering without assets
    (->str [:div \"Hello World\"])
    ;; => \"<div>Hello World</div>\"

    ;; Rendering with asset resolution
    (->str {:hifi/assets asset-resolver}
           [:head
            ^{:hifi.html/asset-marker {:type :hifi.html/stylesheet}}
            [:link {:href \"app.css\"}]])
    ;; => \"<head><link href='/assets/app-abc123.css'></head>\"

  Notes:
    - Asset elements must have :hifi.html/asset-marker metadata to be processed
    - Uses Chassis for HTML compilation under the hood
    - Automatically escapes content to prevent XSS (use `raw` for unescaped HTML)"
  ([ctx root]
   (impl/->str ctx root))
  ([root]
   (impl/->str nil root)))

(defn render
  "Renders Hiccup HTML with asset path resolving and preloads support.

  This function provides a sophisticated hiccup -> html rendering:
  1. Collects asset preloads from the HTML tree for Link preload headers and HTTP 103 Early Hints
  2. Resolves asset paths through the asset pipeline
  3. Optionally delays rendering for performance optimization

  Arguments:
    ctx    - Context map containing:
             :asset-resolver - Asset resolver for path resolution

             all other keys are ignored

    hiccup - hiccup data structure to render

    opts   - (optional) Rendering options map:
             {:collect-preloads? true   - Collect and resolve preload hints (default: true)
              :delay-render?     false} - Delay HTML rendering until deref (default: false)

  Returns:
    Map containing:
      {:preloads [...] - Vector of resolved preload hints for HTTP 103 Early Hints
       :html     \"...\" - Rendered HTML string (nil if delay-render? is true)
       :render_  delay} - Delayed HTML rendering (only if delay-render? is true)

  Examples:
    ;; Basic rendering with preload collection
    (render {:asset-resolver resolver}
            [:html
             [:head
              [:link {:href \"app.css\" :rel \"preload\" :as \"style\"}]]
             [:body \"Content\"]])
    ;; => {:preloads [{:path \"/assets/app-abc123.css\" :rel \"preload\" :as \"style\"}]
    ;;     :html \"<html>...</html>\"}

    ;; Delayed rendering for streaming responses
    (render ctx hiccup {:delay-render? true})
    ;; => {:preloads [...] :render_ #<Delay ...>}
    ;; Later: (force (:render_ result)) to get HTML

  Notes:
    - Preloads are extracted from all <link> and <script> tags in <head>
    - Supports integrity attributes for subresource integrity
    - Delay rendering is useful for streaming responses or lazy evaluation"
  ([ctx hiccup]
   (impl/render ctx hiccup nil))
  ([ctx hiccup opts]
   (impl/render ctx hiccup opts)))

(defn preloads->header
  "Converts preload data into an HTTP Link header value.

  Formats preload directives for HTTP 103 Early Hints (and final response Link headers).
  Builds the header incrementally, stopping when the size limit is reached to
  respect proxy and client header size limits.

  Arguments:
    preloads - Vector of preload maps, each containing:
               {:path \"/path/to/resource\" or :href \"/resolved/path\"
                :rel \"preload\" or \"modulepreload\"
                :as \"style\", \"script\", \"font\", etc. (optional)
                :type \"text/css\", \"font/woff2\", etc. (optional)
                :crossorigin \"anonymous\" (optional)
                :integrity \"sha256-...\" (optional)}
    opts     - (optional) Options map:
               {:max-size 1000} - Maximum header size in bytes (default: 1000)

  Returns:
    String formatted as HTTP Link header value, or nil for empty input.

  Examples:
    (preloads->header [{:path \"/app.css\" :rel \"preload\" :as \"style\"}])
    ;; => \"</app.css>; rel=preload; as=style\"

    (preloads->header [{:path \"/app.css\" :rel \"preload\" :as \"style\"}
                       {:path \"/app.js\" :rel \"preload\" :as \"script\"}])
    ;; => \"</app.css>; rel=preload; as=style, </app.js>; rel=preload; as=script\"

  Notes:
    - Respects header size limits per HTTP best practices (default 1KB)
    - Includes as many links as possible without exceeding the limit
    - Remember max-size is just for this particular value, not all headers
    - Compatible with HTTP 103 Early Hints and standard responses"
  ([preloads]
   (impl/preloads->header preloads nil))
  ([preloads opts]
   (impl/preloads->header preloads opts)))

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

(def doctype-html5
  "RawString for <!DOCTYPE html>"
  chassis/doctype-html5)

(defmethod chassis/resolve-alias ::stylesheet-link
  [_ attrs content]
  (with-meta
    [:link attrs content]
    {::asset-marker {:type ::stylesheet}}))

(defmethod chassis/resolve-alias ::preload-link
  [_ attrs content]
  (with-meta
    [:link attrs content]
    {::asset-marker {:type ::preload}}))

(defmethod chassis/resolve-alias ::javascript-include
  [_ attrs content]
  (with-meta
    [:script attrs content]
    {::asset-marker {:type ::javascript}}))

(defmethod chassis/resolve-alias ::image
  [_ attrs content]
  (with-meta
    [:img attrs content]
    {::asset-marker {:type ::image :opts attrs}}))

(defmethod chassis/resolve-alias ::audio
  [_ attrs content]
  (with-meta
    [:audio attrs content]
    {::asset-marker {:type ::audio :opts attrs}}))

;; TODO future tags: video, picture

(defn csrf-token-input
  "Returns hiccup for <input type=\"hidden\" /> field with the anti-forgery token name and value"
  [req]
  [:input {:type "hidden" :name "__anti-forgery-token" :value (:hifi/csrf-token req)}])

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
                  [:div {:id "hifi-on-load" :data-on-load on-load-js}]
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
