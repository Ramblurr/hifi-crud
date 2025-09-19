(ns hifi.html.protocols)

(defprotocol AssetElementProcessor
  "Protocol for processing hiccup elements that reference assets.

  A processor transforms hiccup elements that carry an `:hifi.html/asset-marker`
  metadata entry. The element passed in is the raw hiccup vector produced by the
  tag helpers (e.g. `stylesheet-link-tag`, `javascript-include-tag`, `image-tag`)
  with all original attributes and metadata intact.

  Implementations MUST:
  - Return a hiccup vector element of the same general shape (`[tag attrs ...]`).
  - Preserve any existing metadata on the element and also add
    `::processed? true` (from this namespace) to mark it as processed.
  - Be idempotent: if given an element that already has `::processed? true`,
    return it unchanged.
  - Only transform the current node; do not attempt to walk or recurse into
    children (the library handles traversal).

  Implementations SHOULD:
  - Rewrite `:src` / `:href` attributes based on the asset system in use
    (e.g., manifest lookup, CDN prefix, fingerprinted paths).
  - Optionally add attributes such as `:integrity`, `:crossorigin`, `:nonce`,
    etc., according to application needs.
  - Leave unrelated attributes from the template untouched.

  The goal is to decouple hiccup generation from the mechanics of locating and
  fingerprinting assets. Different libraries (e.g. hifi-assets, custom CDNs)
  can provide their own processors to handle asset references in HTML elements."
  (rewrite-asset-element [this element]
    "Return an enhanced hiccup element with resolved asset attributes."))

(defprotocol AssetResolver
  (resolve-asset [this path opts]) ;; => {:href "...", :integrity "...", :crossorigin "...", :type "text/css" ...}

  (resolve-preloads [this refs])
  ;; refs: [{:path "css/app.css" :as "style" :type "text/css" :integrity? true :crossorigin "anonymous"} ...]
  ;; :as and :type are optional and detected from mimetype, crossorigin is auto set to anonymous if not provided and integrity? is true
  ;; =>   [{:href "...", :as "style", :type "text/css", :integrity "...", :crossorigin "anonymous"} ...]
  )
