(ns ecommerce.product.handler
  (:require [ecommerce.product.repository :as repo]
            [ecommerce.product.search :as search]
            [ecommerce.validation :as v]
            [clojure.data.json :as json]
            [clojure.string :as str]))

(defn prepare-product
  "Prepare raw input for validation: trim name, coerce price to double."
  [params]
  (cond-> params
    (string? (:name params)) (update :name str/trim)
    (number? (:price params)) (update :price double)))

(defn- validation-errors->details
  "Transform Malli humanized errors into API detail entries.
   Each entry has :field and :reason keys."
  [errors]
  (reduce-kv (fn [acc field messages]
               (into acc (map (fn [msg] {:field (name field) :reason msg})
                              messages)))
             []
             errors))

(defn- json-value-fn
  "Custom value function for data.json to handle DB timestamp types."
  [_key value]
  (cond
    (instance? java.time.OffsetDateTime value) (str value)
    (instance? java.sql.Timestamp value) (str value)
    :else value))

(defn create-product
  "Create-product handler. Takes datasource, returns a Ring handler function (closure pattern)."
  [datasource]
  (fn [request]
    (let [raw-params (:body-params request)
          params (prepare-product (if (map? raw-params) raw-params {}))
          errors (v/validate v/Product params)]
      (if errors
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (json/write-str
                {:error {:code "VALIDATION_ERROR"
                         :message "Product validation failed"
                         :details (validation-errors->details errors)}})}
        (let [product (repo/insert-product! datasource params)]
          {:status 201
           :headers {"Content-Type" "application/json"}
           :body (json/write-str product :value-fn json-value-fn)})))))

(defn update-product
  "Update-product handler. Takes datasource, returns a Ring handler function (closure pattern)."
  [datasource]
  (fn [request]
    (let [url-sku (-> request :path-params :sku)
          raw-params (:body-params request)
          params (prepare-product (if (map? raw-params) raw-params {}))]
      ;; SKU immutability check: body SKU must match URL SKU if present
      (if (and (contains? params :sku)
               (not= (:sku params) url-sku))
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (json/write-str
                {:error {:code "BAD_REQUEST"
                         :message "SKU in body does not match URL parameter"}})}
        ;; Remove :sku from params (it comes from URL, not body)
        (let [body-params (dissoc params :sku)
              errors (v/validate v/UpdateProduct body-params)]
          (if errors
            {:status 400
             :headers {"Content-Type" "application/json"}
             :body (json/write-str
                    {:error {:code "VALIDATION_ERROR"
                             :message "Product validation failed"
                             :details (validation-errors->details errors)}})}
            (let [product (repo/update-product! datasource url-sku body-params)]
              (if product
                {:status 200
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str product :value-fn json-value-fn)}
                {:status 404
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str
                        {:error {:code "NOT_FOUND"
                                 :message "Product not found"}})}))))))))

(defn delete-product
  "Delete-product handler. Takes datasource, returns a Ring handler function (closure pattern)."
  [datasource]
  (fn [request]
    (let [sku (-> request :path-params :sku)
          deleted? (repo/delete-product! datasource sku)]
      (if deleted?
        {:status 204
         :headers {}
         :body nil}
        {:status 404
         :headers {"Content-Type" "application/json"}
         :body (json/write-str
                {:error {:code "NOT_FOUND"
                         :message "Product not found"}})}))))

(defn list-products
  "List-products handler with search, filters, sort, and pagination.
   Takes datasource, returns a Ring handler function (closure pattern)."
  [datasource]
  (fn [request]
    (let [query-params (:query-params request)
          parsed (search/parse-search-params query-params)
          result (search/validate-search-params parsed)]
      (if (:errors result)
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (json/write-str
                {:error {:code "VALIDATION_ERROR"
                         :message "Invalid search parameters"
                         :details (:errors result)}})}
        (let [params (:params result)
              items (repo/search-products datasource params)
              total (repo/count-products datasource params)
              paging-urls (search/build-paging-urls
                           "/api/products" params total)]
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body (json/write-str
                  {:items items
                   :paging (merge {:page (:page params)
                                   :perPage (:per-page params)
                                   :total total}
                                  paging-urls)}
                  :value-fn json-value-fn)})))))

(defn get-product
  "Get-product handler. Returns a single product by SKU.
   Takes datasource, returns a Ring handler function (closure pattern)."
  [datasource]
  (fn [request]
    (let [sku (-> request :path-params :sku)
          product (repo/find-by-sku datasource sku)]
      (if product
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/write-str product :value-fn json-value-fn)}
        {:status 404
         :headers {"Content-Type" "application/json"}
         :body (json/write-str
                {:error {:code "NOT_FOUND"
                         :message "Product not found"}})}))))

(defn list-categories
  "List-categories handler. Returns all distinct product categories.
   Takes datasource, returns a Ring handler function (closure pattern)."
  [datasource]
  (fn [_request]
    (let [categories (repo/list-categories datasource)]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/write-str {:categories categories})})))
