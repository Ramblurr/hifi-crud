(ns hifi.dev-tasks.spec)

(def ^:private tw-default-input "resources/public/tailwind.css")

(def TailwindConfigSchema
  [:map {:name :hifi/tailwindcss}
   [:enabled? {:doc     "Enable Tailwind CSS processing"
               :default true} :boolean]
   [:input {:doc     "Input CSS file for Tailwind CSS"
            :default tw-default-input} :string]
   [:output {:doc     "Output CSS file for Tailwind CSS"
             :default "target/resources/public/compiled.css"} :string]
   [:tw-binary {:default "tailwindcss"
                :desc    "Path to the Tailwind CSS binary"}
    :string]])

(def DatomicConfigSchema
  [:map {:name :hifi/datomic}
   [:enabled? {:doc     "Enable Datomic Pro transactor service in background"
               :default false} :boolean]])
