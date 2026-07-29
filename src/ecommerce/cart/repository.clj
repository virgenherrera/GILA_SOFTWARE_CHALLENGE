(ns ecommerce.cart.repository
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as hsql]))

(defn create-cart!
  "Insert a new cart into the database. Returns the inserted row."
  [datasource]
  (let [cart-id (java.util.UUID/randomUUID)
        query (hsql/format {:insert-into :carts
                            :values [{:id cart-id
                                      :cookie_id (str cart-id)}]
                            :returning [:id :status :created_at :updated_at]})]
    (jdbc/execute-one! datasource query
                       {:builder-fn rs/as-unqualified-maps})))

(defn find-cart-by-id
  "Find a cart by its UUID. Returns nil if not found."
  [datasource cart-id]
  (let [query (hsql/format {:select [:id :status :created_at :updated_at]
                            :from [:carts]
                            :where [:= :id cart-id]})]
    (jdbc/execute-one! datasource query
                       {:builder-fn rs/as-unqualified-maps})))

(defn find-cart-item
  "Find a single cart item by cart-id and product-sku. Returns nil if not found."
  [datasource cart-id product-sku]
  (let [query (hsql/format {:select [:id :cart_id :product_sku :quantity :unit_price_snapshot]
                            :from [:cart_items]
                            :where [:and
                                    [:= :cart_id cart-id]
                                    [:= :product_sku product-sku]]})]
    (jdbc/execute-one! datasource query
                       {:builder-fn rs/as-unqualified-maps})))

(defn add-cart-item!
  "Insert a cart item, or increment quantity if (cart_id, product_sku) already exists.
   The unit_price_snapshot is set on initial insert and kept unchanged on conflict."
  [datasource cart-id product-sku quantity unit-price-snapshot]
  (let [query (hsql/format {:insert-into :cart_items
                            :values [{:cart_id cart-id
                                      :product_sku product-sku
                                      :quantity quantity
                                      :unit_price_snapshot unit-price-snapshot}]
                            :on-conflict [:cart_id :product_sku]
                            :do-update-set {:quantity [:+ :cart_items.quantity :excluded.quantity]}
                            :returning [:id :product_sku :quantity :unit_price_snapshot]})]
    (jdbc/execute-one! datasource query
                       {:builder-fn rs/as-unqualified-maps})))

(defn update-cart-item-quantity!
  "Update the quantity of a cart item. Returns the updated row, or nil if not found."
  [datasource cart-id product-sku quantity]
  (let [query (hsql/format {:update :cart_items
                            :set {:quantity quantity}
                            :where [:and
                                    [:= :cart_id cart-id]
                                    [:= :product_sku product-sku]]
                            :returning [:id :product_sku :quantity :unit_price_snapshot]})]
    (jdbc/execute-one! datasource query
                       {:builder-fn rs/as-unqualified-maps})))

(defn delete-cart-item!
  "Delete a cart item by cart-id and product-sku.
   Returns true if a row was deleted, false if not found."
  [datasource cart-id product-sku]
  (let [query (hsql/format {:delete-from :cart_items
                            :where [:and
                                    [:= :cart_id cart-id]
                                    [:= :product_sku product-sku]]})
        result (jdbc/execute-one! datasource query)]
    (pos? (:next.jdbc/update-count result))))

(defn get-cart-items
  "Get all items in a cart, joined with products for the product name.
   Returns a vector of maps with :product_sku, :name, :quantity, :unit_price_snapshot."
  [datasource cart-id]
  (let [query (hsql/format {:select [[:ci.product_sku :product_sku]
                                     [:p.name :name]
                                     [:ci.quantity :quantity]
                                     [:ci.unit_price_snapshot :unit_price_snapshot]]
                            :from [[:cart_items :ci]]
                            :join [[:products :p] [:= :ci.product_sku :p.sku]]
                            :where [:= :ci.cart_id cart-id]
                            :order-by [[:ci.created_at :asc]]})]
    (jdbc/execute! datasource query
                   {:builder-fn rs/as-unqualified-maps})))

(defn update-cart-timestamp!
  "Update the updated_at timestamp of a cart to now()."
  [datasource cart-id]
  (let [query (hsql/format {:update :carts
                            :set {:updated_at [:raw "now()"]}
                            :where [:= :id cart-id]})]
    (jdbc/execute-one! datasource query)))

(defn find-product-by-sku
  "Find a product by SKU, returning sku, name, price, and stock."
  [datasource sku]
  (let [query (hsql/format {:select [:sku :name :price :stock]
                            :from [:products]
                            :where [:= :sku sku]})]
    (jdbc/execute-one! datasource query
                       {:builder-fn rs/as-unqualified-maps})))
