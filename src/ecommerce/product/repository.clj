(ns ecommerce.product.repository
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as hsql]))

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
