;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; Portions of this file are based on hyperlith code from @Anders
;; https://github.com/andersmurphy/hyperlith/
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.datastar.util)

(defmacro while-some
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings & body]
  `(loop []
     (when-some ~bindings
       ~@body
       (recur))))
