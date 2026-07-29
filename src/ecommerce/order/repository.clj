(ns ecommerce.order.repository
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as hsql]))

(defn lock-products-for-update!
  "Lock product rows for the given SKUs using SELECT FOR UPDATE.
   Must be called within a transaction. Returns a vector of product maps."
  [tx product-skus]
  (let [query (hsql/format {:select [:sku :name :price :stock]
                            :from [:products]
                            :where [:in :sku product-skus]
                            :for :update})]
    (jdbc/execute! tx query {:builder-fn rs/as-unqualified-maps})))

(defn decrement-product-stock!
  "Decrement the stock of a product by the given quantity.
   Must be called within a transaction."
  [tx sku quantity]
  (let [query (hsql/format {:update :products
                            :set {:stock [:- :stock quantity]}
                            :where [:= :sku sku]})]
    (jdbc/execute-one! tx query)))

(defn create-order!
  "Insert a new order. Returns the inserted row with id, status, total, created_at."
  [tx cart-id total]
  (let [query (hsql/format {:insert-into :orders
                            :values [{:cart_id cart-id
                                      :status "Paid"
                                      :total total}]
                            :returning [:id :status :total :created_at]})]
    (jdbc/execute-one! tx query {:builder-fn rs/as-unqualified-maps})))

(defn create-order-item!
  "Insert an order item. Returns the inserted row."
  [tx order-id product-sku quantity price-at-purchase]
  (let [query (hsql/format {:insert-into :order_items
                            :values [{:order_id order-id
                                      :product_sku product-sku
                                      :quantity quantity
                                      :price_at_purchase price-at-purchase}]
                            :returning [:id :product_sku :quantity :price_at_purchase]})]
    (jdbc/execute-one! tx query {:builder-fn rs/as-unqualified-maps})))

(defn update-cart-status!
  "Update the status and updated_at timestamp of a cart."
  [tx cart-id status]
  (let [query (hsql/format {:update :carts
                            :set {:status status
                                  :updated_at [:raw "now()"]}
                            :where [:= :id cart-id]})]
    (jdbc/execute-one! tx query)))

(defn find-order-by-id
  "Find an order by its UUID. Returns nil if not found."
  [connectable order-id]
  (let [query (hsql/format {:select [:id :cart_id :status :total :created_at]
                            :from [:orders]
                            :where [:= :id order-id]})]
    (jdbc/execute-one! connectable query {:builder-fn rs/as-unqualified-maps})))

(defn find-order-items
  "Find all items for an order, joined with products for the product name.
   Returns a vector of maps with :product_sku, :name, :quantity, :price_at_purchase."
  [connectable order-id]
  (let [query (hsql/format {:select [[:oi.product_sku :product_sku]
                                     [:p.name :name]
                                     [:oi.quantity :quantity]
                                     [:oi.price_at_purchase :price_at_purchase]]
                            :from [[:order_items :oi]]
                            :join [[:products :p] [:= :oi.product_sku :p.sku]]
                            :where [:= :oi.order_id order-id]
                            :order-by [[:oi.created_at :asc]]})]
    (jdbc/execute! connectable query {:builder-fn rs/as-unqualified-maps})))
