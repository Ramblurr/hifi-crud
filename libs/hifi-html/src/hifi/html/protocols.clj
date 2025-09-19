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
  "Protocol for resolving logical asset paths to their final URLs and related metadata.

  Asset resolvers handle the translation from logical paths (e.g., \"app.css\") to
  their final form in the rendered HTML.

  Implementations might include manifest-based resolvers (for production builds
  with fingerprinted assets), CDN resolvers, or development resolvers that serve
  assets directly from the filesystem."

  (resolve-path [this logical-path]
    "Resolves a logical asset path to its public URL.

    Takes a logical path (e.g., \"css/app.css\", \"images/logo.png\") and returns
    the full URL path that should be used in the HTML (e.g., \"/assets/app-abc123.css\",
    \"https://cdn.example.com/assets/logo-xyz789.png\").

    Returns nil if the asset cannot be resolved.")

  (integrity [this logical-path]
    "Returns the Subresource Integrity (SRI) hash for the asset.

    Returns a string suitable for the integrity attribute (e.g., \"sha384-...\")
    or nil if integrity checking is not available/configured for this asset.

    SRI provides security by ensuring the browser only executes resources that
    match the expected cryptographic hash.")

  (read-bytes [this logical-path]
    "Returns an InputStream for the compiled asset bytes.

    Returns a java.io.InputStream for the compiled asset content, or nil if not found.
    MUST be closed by the caller to avoid resource leaks.")

  (locate [this logical-path]
    "Returns a java.nio.file.Path to the compiled file on disk.

    Returns a java.nio.file.Path pointing to the compiled asset's location on disk,
    or nil when not file-backed."))
