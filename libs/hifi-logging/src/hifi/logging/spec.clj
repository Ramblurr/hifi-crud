(ns hifi.logging.spec)

(def ConsoleLoggingComponentOptions
  [:map {:name ::logging-console}
   [:enabled? {:doc "Enable logging to stdout" :default true} :boolean]
   [:format {:doc "What format should the logging to stdout take?" :default :pretty} [:enum :json :edn :pretty]]])

(def TelemereTapHandlerComponentOptions
  [:map {:name ::logging-tap}
   [:enabled? {:default true} :boolean]])
