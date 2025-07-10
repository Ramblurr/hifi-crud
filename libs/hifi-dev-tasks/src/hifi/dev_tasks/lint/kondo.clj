(ns hifi.dev-tasks.lint.kondo
  (:require
   [clojure.string :as str]
   [babashka.tasks :refer [shell]]))

(defn lint-main
  "Lints project using clj-kondo or lint a specific path"
  [& args]
  (shell (str "clj-kondo" " --fail-level error"
              (if (seq args)
                (str " --lint " (first args))
                " --lint src --lint test"))))

(defn copy-configs-main
  "Copies clj-kondo configs from deps"
  [& _]
  (let [cp (-> (shell {:out :string} "clojure -Spath -A:test:dev") :out str/trim)]
    (shell (str "clj-kondo --lint " cp " --dependencies --copy-configs --skip-lint"))))
