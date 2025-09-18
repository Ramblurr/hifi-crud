# hifi-assets

hifi-assets is a Clojure asset pipeline inspired by Rails' Propshaft.
Designed for simplicity and maintainability.

It provides digest-based asset management for cache busting and efficient delivery, but also developer friendly ergonomics.

The asset pipeline discovers assets from known sources, generates digests, builds a manifest, and processes internal references.


TODO expand on these points

- hifi-assets enumerates every file in its list of asset paths and copies them into the target/resources/public/assets
- apps configure the asset paths (default "assets/" from project root)
- libraries can also define asset paths TODO
- asset dirs can be exclueded
- Configuration is data-driven and extensible, allowing custom processors per mime type.
- config format is a map defined in libs/hifi-assets/src/hifi/assets/spec.clj example in examples/real-crud-app/resources/env.edn nd the assets test
- it digests the file, adds the digest to the filename, and then runs a set of processors
- bypassing the digest is possible by naming the file like  `-[digest].digested.js`
- Processing involves adding the digest to the filename and then running a set of "processors" that process the file’s content.
- Processors transform content and resolve dependencies like CSS urls or JS asset helpers.
- HIFI_ASSET_URL processor for js files,  this.img = HIFI_ASSET_URL("/icons/trash.svg") ->     this.img = "/assets/icons/trash-54g9cbef.svg"
