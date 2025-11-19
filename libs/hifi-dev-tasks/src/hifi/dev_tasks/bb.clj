;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.bb
  (:require
   [bling.core :as bling]
   [bblgum.core :refer [gum]]
   [clojure.edn :as edn]
   [lambdaisland.deep-diff2 :as dd]
   [borkdude.rewrite-edn :as r]))

(def default-deps {'hifi/dev-tasks {:local/root "../../libs/hifi-dev-tasks"}})
(def default-pods {'clj-kondo/clj-kondo {:version "2025.06.05"}})
(def default-tasks
  {'clean              'hifi.dev-tasks.build/clean
   'css                'hifi.dev-tasks.css.tailwind/build-dev
   'css:prod           'hifi.dev-tasks.css.tailwind/build-prod
   'css:watch          'hifi.dev-tasks.css.tailwind/watch-dev
   'datomic            'hifi.dev-tasks.datomic/-main
   'dev                'hifi.dev-tasks.dev/-main
   'fmt                'hifi.dev-tasks.fmt/fmt-main
   'lint:carve         'hifi.dev-tasks.lint.carve/-main
   'lint:copy-configs  'hifi.dev-tasks.lint.kondo/copy-configs-main
   'lint               'hifi.dev-tasks.lint.kondo/lint-main
   'lint:ns-docstrings 'hifi.dev-tasks.lint.ns-docstrings/-main
   'uber               'hifi.dev-tasks.build/uber
   'bun                'hifi.dev-tasks.bun/build-dev
   'importmap          'hifi.dev-tasks.importmap/-main})

(defn- read-bb-edn
  []
  (r/parse-string (slurp "bb.edn")))

(defn- update-map [m path bb]
  (reduce
   (fn [bb [name value]] (r/assoc-in bb
                                     (conj path name)
                                     value))
   bb
   m))

(defn- update-tasks
  "Update the bb.edn file with the latest tasks."
  [existing-bb]
  (update-map default-tasks [:tasks] existing-bb))

(defn- update-pods
  [existing-bb]
  (update-map default-pods [:pods] existing-bb))

(defn- update-deps
  [existing-bb]
  (update-map default-deps [:deps] existing-bb))

(defn updated-bb-edn [before]
  (-> before
      (update-tasks)
      (update-pods)
      (update-deps)))

(defn diff-changes [before after]
  (let [diff (dd/diff (-> before str edn/read-string)
                      (-> after str edn/read-string))
        min  (dd/minimize diff)]
    (if (= {} min)
      nil
      min)))

(defn prompt-change [diff force?]
  (if diff
    (do
      (println (bling/bling [:bold.warning "\nChanges detected in bb.edn:\n"]))
      (dd/pretty-print diff)
      (println)
      (or force?
          (:result (gum :confirm ["Apply changes?"] :as  :bool
                        :negative "No" :affirmative "Yes" :default false))))
    (println (bling/bling [:bold.positive "No updates needed in bb.edn."]))))

(defn force? [args]
  (some
   #{"-f" "--force"}
   args))

(defn update-bb-edn
  "Update the bb.edn file with the latest configuration."
  [& args]
  (let [before (read-bb-edn)
        after  (updated-bb-edn before)
        diff   (diff-changes before after)]
    (when (prompt-change diff (force? args))
      (spit "bb.edn" (str after))
      (println (bling/bling [:bold.positive "bb.edn updated successfully."])))))

#_(defn generate-bb-edn
    "Generate the bb.edn file with the default configuration."
    []
    (if (fs/exists? "bb.edn")
      (do
        (println "bb.edn already exists. Skipping generation."))
      (let [bb-edn {:deps  default-deps
                    :pods  default-pods
                    :tasks default-tasks}]

        #_(spit "bb.edn" (str bb-edn))
        (println (str bb-edn)))))
