(ns ecommerce.product.repository
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as hsql]
            [ecommerce.product.search :as search]))

(defn insert-product!
  "Insert a product into the database. Returns the inserted row.
   Throws ExceptionInfo with {:status 409} on duplicate SKU."
  [datasource product-map]
  (try
    (let [query (hsql/format {:insert-into :products
                              :values [product-map]
                              :returning [:sku :name :description :price
                                          :category :stock
                                          :created_at :updated_at]})]
      (jdbc/execute-one! datasource query
                         {:builder-fn rs/as-unqualified-maps}))
    (catch java.sql.SQLException e
      (if (= "23505" (.getSQLState e))
        (throw (ex-info (str "A product with SKU '" (:sku product-map) "' already exists")
                        {:status 409
                         :code "CONFLICT"
                         :message (str "A product with SKU '" (:sku product-map) "' already exists")}))
        (throw e)))))

(defn find-by-sku
  "Find a product by SKU. Returns nil if not found."
  [datasource sku]
  (let [query (hsql/format {:select [:sku :name :description :price
                                     :category :stock
                                     :created_at :updated_at]
                            :from [:products]
                            :where [:= :sku sku]})]
    (jdbc/execute-one! datasource query
                       {:builder-fn rs/as-unqualified-maps})))

(defn update-product!
  "Update a product by SKU. Returns the updated row, or nil if SKU not found.
   Sets updated_at to now()."
  [datasource sku product-map]
  (let [set-clause (assoc product-map :updated_at [:raw "now()"])
        query (hsql/format {:update :products
                            :set set-clause
                            :where [:= :sku sku]
                            :returning [:sku :name :description :price
                                        :category :stock
                                        :created_at :updated_at]})]
    (jdbc/execute-one! datasource query
                       {:builder-fn rs/as-unqualified-maps})))

(defn delete-product!
  "Delete a product by SKU. Returns true if a row was deleted, false if not found.
   Throws ExceptionInfo with {:status 409 :code \"PRODUCT_IN_USE\"} on FK violation."
  [datasource sku]
  (try
    (let [query (hsql/format {:delete-from :products
                              :where [:= :sku sku]})
          result (jdbc/execute-one! datasource query)]
      (pos? (:next.jdbc/update-count result)))
    (catch java.sql.SQLException e
      (if (= "23503" (.getSQLState e))
        (throw (ex-info (str "Cannot delete product '" sku "': referenced by existing orders or cart items")
                        {:status 409
                         :code "PRODUCT_IN_USE"
                         :message (str "Cannot delete product '" sku "': referenced by existing orders or cart items")}))
        (throw e)))))

(defn search-products
  "Search products with filters, sort, and pagination.
   Takes validated search params, returns a vector of product maps."
  [datasource params]
  (let [query (hsql/format (search/build-search-query params))]
    (jdbc/execute! datasource query
                   {:builder-fn rs/as-unqualified-maps})))

(defn count-products
  "Count products matching the same filters as search-products.
   Takes validated search params, returns total count as a long."
  [datasource params]
  (let [query (hsql/format (search/build-count-query params))
        result (jdbc/execute-one! datasource query
                                  {:builder-fn rs/as-unqualified-maps})]
    (or (:total result) 0)))

(defn list-categories
  "List all distinct product categories, sorted alphabetically."
  [datasource]
  (let [query (hsql/format {:select-distinct [:category]
                            :from [:products]
                            :where [:<> :category nil]
                            :order-by [[:category :asc]]})]
    (mapv :category
          (jdbc/execute! datasource query
                         {:builder-fn rs/as-unqualified-maps}))))
