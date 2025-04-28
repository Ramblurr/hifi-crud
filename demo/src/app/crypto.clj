;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT


(ns app.crypto
  (:require
   [exoscale.cloak :as cloak]
   [hyperlith.impl.crypto :as hc]
   [taoensso.tempel :as tempel]
   [taoensso.nippy  :as nippy]))

(comment
  (tempel/keychain-encrypt (tempel/keychain)
                           {:password "root-secret-password"})
  (def tmp (tempel/keychain {}))
  @tmp

;;;;
  )

(defn create-root-key [password]
  (tempel/keychain-encrypt (tempel/keychain)
                           {:pbkdf-nwf :ref-2000-msecs
                            :password  password}))

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
