;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: EUPL-1.2
(ns hifi.web.middleware.cookies
  (:require
   [ring.middleware.cookies :as cookies]))

(defn cookies-middleware
  "Parses the cookies in the request map, then assocs the resulting map
  to the :cookies key on the request.

  Accepts the following options:

  :decoder - a function to decode the cookie value. Expects a function that
             takes a string and returns a string. Defaults to URL-decoding.

  :encoder - a function to encode the cookie name and value. Expects a
             function that takes a name/value map and returns a string.
             Defaults to URL-encoding.

  Each cookie is represented as a map, with its value being held in the
  :value key. A cookie may optionally contain a :path, :domain or :port
  attribute.

  To set cookies, add a map to the :cookies key on the response. The values
  of the cookie map can either be strings, or maps containing the following
  keys:

  :value     - the new value of the cookie
  :path      - the subpath the cookie is valid for
  :domain    - the domain the cookie is valid for
  :max-age   - the maximum age in seconds of the cookie
  :expires   - a date string at which the cookie will expire
  :secure    - set to true if the cookie requires HTTPS, prevent HTTP access
  :http-only - set to true if the cookie is valid for HTTP and HTTPS only
               (ie. prevent JavaScript access)
  :same-site - set to :strict or :lax to set SameSite attribute of the cookie"
  [options]
  {:name ::cookies
   :wrap (fn cookies-wrap [handler]
           (fn cookies-middleware
             ([request]
              (-> request
                  (cookies/cookies-request options)
                  handler
                  (cookies/cookies-response options)))
             ([request respond raise]
              (handler (cookies/cookies-request request options)
                       (fn [response] (respond (cookies/cookies-response response options)))
                       raise))))})
