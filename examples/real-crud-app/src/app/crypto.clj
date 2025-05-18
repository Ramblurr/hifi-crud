;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns app.crypto
  (:require
   [exoscale.cloak :as cloak]
   [taoensso.tempel :as tempel]
   [app.db :as d]))

(comment
  (tempel/keychain-encrypt (tempel/keychain)
                           {:password "root-secret-password"})
  (def tmp (tempel/keychain {}))
  @tmp

;;;;
  )

(defn create-root-key [password]
  (assert (>  (count (cloak/unmask password)) 0)
          "The root password must be provided")
  (tempel/keychain-encrypt (tempel/keychain)
                           {:pbkdf-nwf :ref-2000-msecs
                            :password  (cloak/unmask password)}))

(defn create-user-keychain
  "Creates a new encrypted `KeyChain` for user given the cloaked user password"
  [root-public-keychain user-password]
  (assert root-public-keychain "The root public keychain must be provided")
  (assert (cloak/unmask user-password) "The user's password must be provided")
  (let [;; Create a new encrypted `KeyChain` for user.
        encrypted-keychain
        (tempel/keychain-encrypt (tempel/keychain {})
                                 {:password   (cloak/unmask user-password)
                                  :backup-key root-public-keychain})]
    encrypted-keychain))

(defn ensure-root-user!
  "Ensures the root/admin user exists in the database."
  [root-secret conn]
  (when (not (d/find-by (d/db conn) :user/email "admin" [:user/email]))
    @(d/tx! conn [{:user/id       (d/squuid)
                   :user/email    "admin"
                   :user/keychain (create-root-key root-secret)}])))

(defn root-public-keychain
  "Return the public keychain of the root user."
  [conn]
  (->
   (d/find-by (d/db conn) :user/email "admin" [:user/keychain])
   :user/keychain
   tempel/public-data
   :keychain))

(defn unlock-root-keychain [{:keys [root-secret]} conn]
  (assert conn "No connection to Datahike")
  (assert root-secret "No root secret provided")
  (ensure-root-user! root-secret conn)
  {:app/root-public-keychain (root-public-keychain conn)})
