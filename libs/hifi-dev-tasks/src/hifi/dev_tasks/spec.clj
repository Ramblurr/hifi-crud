;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.dev-tasks.spec
  (:require
   [babashka.fs :as fs]))

(def ^:private tw-default-input "assets/css/tailwind.css")

(def TailwindConfigSchema
  [:map {:name :hifi/tailwindcss}
   [:enabled? {:doc     "Enable Tailwind CSS processing"
               :default true} :boolean]
   [:input {:doc     "Input CSS file for Tailwind CSS"
            :default tw-default-input} :string]
   [:output {:doc     "Output CSS file for Tailwind CSS"
             :default "assets/builds/tailwind.css"} :string]
   [:path {:default "tailwindcss"
           :desc    "Path to the Tailwind CSS binary or the command if it is on the PATH"} :string]])

(def DatomicConfigSchema
  [:map {:name :hifi/datomic}
   [:enabled? {:doc     "Enable Datomic Pro transactor service in background"
               :default false} :boolean]])

(def BunProfileTargetSchema
  [:map {:name :hifi/bun-profile-environment}
   [:args {:doc     "Arguments to pass to the Bun command"
           :default []} [:vector :string]]])
(def BunProfileSchema
  [:map {:name :hifi/bun-profile}
   [:cd {:doc     "Change directory to this path before running the Bun command"
         :default (fs/cwd)} :string]
   [:env {:doc     "Environment variables to set for the Bun command"
          :default {}} [:map-of :string :string]]
   [:args {:doc "Arguments to pass to the Bun command"} [:vector :string]]
   [:dev {:doc "Dev specific config" :default {}} BunProfileTargetSchema]
   [:prod {:doc "Production specific config" :default {}} BunProfileTargetSchema]])

(def BunConfigSchema
  [:map {:name :hifi/bun}
   [:enabled? {:doc     "Enable Bun asset pipeline"
               :default true} :boolean]
   [:profiles {:doc "Bun profiles to use for building assets"} [:map-of :keyword BunProfileSchema]]
   [:path {:default "bun"
           :desc    "Path to the bun binary or the command if it is on the PATH"} :string]])
