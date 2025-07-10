;; Copyright © 2022 Logseq
;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; From https://github.com/logseq/dev-tasks/blob/acb3d3d5d38c4ac16f617cb10ae6f99fe1b8de6e/src/logseq/bb_tasks/lint/ns_docstrings.clj
;; SPDX-License-Identifier: MIT
(ns hifi.dev-tasks.lint.ns-docstrings
  "Provides lint task to detect all desired namespaces are documented.
  Documented namespaces help teams distribute their knowledge"
  (:require [pod.borkdude.clj-kondo :as clj-kondo]
            [hifi.dev-tasks.config :as config]))

(defn- get-undocumented-namespaces [paths {:keys [ignore-regex] :as config}]
  (let [{{:keys [namespace-definitions]} :analysis}
        (clj-kondo/run! {:lint   paths
                         :config {:output {:analysis true}}})
        ignore-var?             (if ignore-regex
                                  #(re-find (re-pattern ignore-regex) %)
                                  (constantly false))
        undocumented-namespaces (filter
                                 (fn [{:keys [name doc no-doc lang]}]
                                   (and (not doc)
                                        (not no-doc)
                                        ;; Most langs are weirdly nil
                                        (contains? #{nil (:lang config)} lang)
                                        (not (ignore-var? (str name)))))
                                 namespace-definitions)]
    undocumented-namespaces))

(defn- read-config
  []
  (merge
   {:lang :cljs}
   (:ns-docstrings (config/read-tasks-config))))

(defn -main
  "Lint given classpath for namespaces missing docstrings."
  [& args]
  (let [config     (read-config)
        paths      (or (seq args)
                       (:paths config)
                       ["src"])
        namespaces (get-undocumented-namespaces paths config)]
    (if (seq namespaces)
      (do
        (println "\nThe following namespaces are undocumented:")
        (run! println (map :name namespaces))
        (System/exit 1))
      (println "\nAll namespaces are documented!"))))
