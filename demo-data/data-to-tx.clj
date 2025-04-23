#!/usr/bin/env bb

(require
 '[clojure.java.io :as io]
 '[clojure.string :as str])

;; ---- Helper functions ----

(defn generate-uuid []
  (java.util.UUID/randomUUID))

(defn parse-instant [date-str]
  (when (and date-str (not= date-str "null"))
    (java.sql.Date/from
     (.toInstant
      (java.time.OffsetDateTime/parse date-str)))))

(defn create-tempid [entity-type id]
  (str entity-type "-" id))

;; ---- Entity processors ----

(defn process-categories [categories]
  (mapcat
   (fn [category]
     (let [id (generate-uuid)
           tempid (create-tempid "category" (:id category))]
       [[:db/add tempid :category/id id]
        [:db/add tempid :category/name (:name category)]]))
   categories))

(defn process-customers [customers]
  (mapcat
   (fn [customer]
     (let [id      (generate-uuid)
           tempid  (create-tempid "customer" (:id customer))
           base-tx [[:db/add tempid :customer/id id]
                    [:db/add tempid :customer/first-name (:first_name customer)]
                    [:db/add tempid :customer/last-name (:last_name customer)]
                    [:db/add tempid :customer/email (:email customer)]
                    [:db/add tempid :customer/has-ordered (:has_ordered customer)]
                    [:db/add tempid :customer/has-newsletter (:has_newsletter customer)]
                    [:db/add tempid :customer/total-spent (bigdec (:total_spent customer))]]

           ;; Optional attributes
           optional-tx (remove
                        nil?
                        [;; Handle nullable fields
                         (when-let [v (:address customer)]
                           (when-not (= v "null")
                             [:db/add tempid :customer/address v]))

                         (when-let [v (:zipcode customer)]
                           (when-not (= v "null")
                             [:db/add tempid :customer/zipcode v]))

                         (when-let [v (:city customer)]
                           (when-not (= v "null")
                             [:db/add tempid :customer/city v]))

                         (when-let [v (:stateAbbr customer)]
                           (when-not (= v "null")
                             [:db/add tempid :customer/state-abbr v]))

                         (when-let [v (:birthday customer)]
                           (when-not (= v "null")
                             [:db/add tempid :customer/birthday (subs v 0 10)]))

                         (when-let [v (:first_seen customer)]
                           [:db/add tempid :customer/first-seen (parse-instant v)])

                         (when-let [v (:last_seen customer)]
                           [:db/add tempid :customer/last-seen (parse-instant v)])

                         (when-let [v (:latest_purchase customer)]
                           (when-not (= v "null")
                             [:db/add tempid :customer/latest-purchase (parse-instant v)]))])

           ;; Groups (cardinality many)
           groups-tx (map #(vector :db/add tempid :customer/groups %) (:groups customer))]

       (concat base-tx optional-tx groups-tx)))
   customers))

(defn process-products [products]
  (mapcat
   (fn [product]
     (let [id (generate-uuid)
           tempid (create-tempid "product" (:id product))
           category-tempid (create-tempid "category" (:category_id product))]
       [[:db/add tempid :product/id id]
        [:db/add tempid :product/category category-tempid]
        [:db/add tempid :product/reference (:reference product)]
        [:db/add tempid :product/width (long (:width product))]
        [:db/add tempid :product/height (long (:height product))]
        [:db/add tempid :product/price (bigdec (:price product))]
        [:db/add tempid :product/thumbnail (:thumbnail product)]
        [:db/add tempid :product/image (:image product)]
        [:db/add tempid :product/description (:description product)]
        [:db/add tempid :product/stock (long (:stock product))]
        [:db/add tempid :product/sales (long (:sales product))]]))
   products))

(defn process-orders [orders]
  (let [basket-item-tx (atom [])]
    (concat
     (mapcat
      (fn [order]
        (let [id              (generate-uuid)
              tempid          (create-tempid "order" (:id order))
              customer-tempid (create-tempid "customer" (:customer_id order))
              ;; Process basket items and collect their tempids
              basket-items    (map-indexed
                               (fn [idx item]
                                 (let [basket-id      (generate-uuid)
                                       basket-tempid  (create-tempid "basket" (str (:id order) "-" idx))
                                       product-tempid (create-tempid "product" (:product_id item))]
                                   ;; Add basket item transactions to our collection
                                   (swap! basket-item-tx concat
                                          [[:db/add basket-tempid :basket-item/id basket-id]
                                           [:db/add basket-tempid :basket-item/product product-tempid]
                                           [:db/add basket-tempid :basket-item/quantity (long (:quantity item))]])
                                   ;; Return the tempid for the order's basket-items reference
                                   basket-tempid))
                               (:basket order))

              ;; Convert status string to enum reference
              status-enum (keyword "order-status" (:status order))]

          (concat
           [[:db/add tempid :order/id id]
            [:db/add tempid :order/reference (:reference order)]
            [:db/add tempid :order/date (parse-instant (:date order))]
            [:db/add tempid :order/customer customer-tempid]
            [:db/add tempid :order/total-ex-taxes (bigdec (:total_ex_taxes order))]
            [:db/add tempid :order/delivery-fees (bigdec (:delivery_fees order))]
            [:db/add tempid :order/tax-rate (bigdec (:tax_rate order))]
            [:db/add tempid :order/taxes (bigdec (:taxes order))]
            [:db/add tempid :order/total (bigdec (:total order))]
            [:db/add tempid :order/status status-enum]
            [:db/add tempid :order/returned (:returned order)]]

           ;; Add basket-items (cardinality many)
           (map #(vector :db/add tempid :order/basket-items %) basket-items))))
      orders)
     @basket-item-tx)))

(defn process-invoices [invoices]
  (mapcat
   (fn [invoice]
     (let [id              (generate-uuid)
           tempid          (create-tempid "invoice" (:id invoice))
           order-tempid    (create-tempid "order" (:order_id invoice))
           customer-tempid (create-tempid "customer" (:customer_id invoice))]
       [[:db/add tempid :invoice/id id]
        [:db/add tempid :invoice/date (parse-instant (:date invoice))]
        [:db/add tempid :invoice/order order-tempid]
        [:db/add tempid :invoice/customer customer-tempid]
        [:db/add tempid :invoice/total-ex-taxes (bigdec (:total_ex_taxes invoice))]
        [:db/add tempid :invoice/delivery-fees (bigdec (:delivery_fees invoice))]
        [:db/add tempid :invoice/tax-rate (bigdec (:tax_rate invoice))]
        [:db/add tempid :invoice/taxes (bigdec (:taxes invoice))]
        [:db/add tempid :invoice/total (bigdec (:total invoice))]]))
   invoices))

(defn process-reviews [reviews]
  (mapcat
   (fn [review]
     (let [id              (generate-uuid)
           tempid          (create-tempid "review" (:id review))
           order-tempid    (create-tempid "order" (:order_id review))
           product-tempid  (create-tempid "product" (:product_id review))
           customer-tempid (create-tempid "customer" (:customer_id review))
           ;; Convert status string to enum reference
           status-enum     (keyword "review-status" (:status review))]
       [[:db/add tempid :review/id id]
        [:db/add tempid :review/date (parse-instant (:date review))]
        [:db/add tempid :review/status status-enum]
        [:db/add tempid :review/order order-tempid]
        [:db/add tempid :review/product product-tempid]
        [:db/add tempid :review/customer customer-tempid]
        [:db/add tempid :review/rating (long (:rating review))]
        [:db/add tempid :review/comment (:comment review)]]))
   reviews))

;; ---- Main processing logic ----

(defn process-json-data [json-data]
  (let [categories (:categories json-data)
        customers  (:customers json-data)
        products   (:products json-data)
        orders     (:orders json-data)
        invoices   (:invoices json-data)
        reviews    (:reviews json-data)]

    (concat
     #_[[:db/add "order-status-ordered" :db/ident :order-status/ordered]
        [:db/add "order-status-delivered" :db/ident :order-status/delivered]
        [:db/add "order-status-cancelled" :db/ident :order-status/cancelled]
        [:db/add "review-status-accepted" :db/ident :review-status/accepted]
        [:db/add "review-status-rejected" :db/ident :review-status/rejected]
        [:db/add "review-status-pending" :db/ident :review-status/pending]]

     ;; Process each entity type
     (process-categories categories)
     (process-customers customers)
     (process-products products)
     (process-orders orders)
     (process-invoices invoices)
     (process-reviews reviews))))

(defn -main []
  (let [json-file "data.json"
        json-data (json/parse-string (slurp json-file) true)
        tx-data   (process-json-data json-data)]

    ;; Print result as an EDN vector
    (println (str/join "\n" ["[" (str/join "\n " tx-data) "]"]))))

;; Run the main function
(-main)
