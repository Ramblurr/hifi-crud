;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.config
  (:refer-clojure :exclude [mask])
  (:require
   [hifi.config.impl :as impl]))

(defn mask
  "Mask a value behind the `Secret` type, hiding its real value when printing"
  [x]
  (impl/mask x))

(defn unmask
  "Reveals all potential secrets from `x`, returning the value with secrets
  unmasked, works on any walkable type"
  [x]
  (impl/unmask x))

(defn unmask!
  "Like [[unmask]] but throws if the unmasked value is nil"
  [x]
  (impl/unmask! x))

(defn secret?
  "Returns true if x is a value wrapped by the Secret type"
  [x]
  (impl/secret? x))

(defn secret-present?
  "Returns true if x is a value wrapped by the Secret type and the wrapped value is not nil, false otherwise"
  [x]
  (impl/secret-present? x))

(defn mask-deep
  "Recursively masks all leaf values in a tree structure."
  [x]
  (impl/mask-deep x))

(defn read-config
  "Reads an Aero configuration file and returns the parsed configuration map.

  Arguments:
  - `source`: Anything coercible to a reader via `clojure.java.io/reader` - a file path,
    URL, `java.io.File`, resource from `io/resource`, `StringReader`, etc.
  - `opts`: Optional map with configuration options:
    - `:profile` - Profile to use for `#profile` extension (defaults to `hifi.core/current-profile`)
    - `:user` - Manually set the user for the `#user` extension
    - `:resolver` - A function or map used to resolve includes
    - `:hifi/sops` - Options passed to ol.sops

  Supports Aero reader literals (`#profile`, `#user`, `#include`) and custom extensions
  like `#hifi/secret` and `#hifi/sops`.

  Example:

  ```clojure
  ;; Read from classpath
  (read-config (io/resource \"config.edn\") {})

  ;; Read from filesystem
  (read-config \"config.edn\" {:profile :prod})

  ;; Read from string
  (read-config (StringReader. \"{:foo 1}\") {})
  ```"
  [source opts]
  (impl/read-config source opts))
