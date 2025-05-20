(ns hifi.logging.spec)

(def ConsoleLoggingComponentOptions
  [:map {:name ::logging-console}
   [:enabled? {:default true} :boolean]
   [:format {:default :pretty} [:enum :json :edn :pretty]]])

(def TelemereTapHandlerComponentOptions
  [:map {:name ::logging-tap}
   [:enabled? {:default true} :boolean]])
