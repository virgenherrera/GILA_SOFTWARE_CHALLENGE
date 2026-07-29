(ns ecommerce.cart.handler
  (:require [ecommerce.cart.repository :as repo]
            [ecommerce.db :as db]
            [clojure.data.json :as json]))

(defn- json-value-fn
  "Custom value function for data.json to handle DB timestamp types."
  [_key value]
  (cond
    (instance? java.time.OffsetDateTime value) (str value)
    (instance? java.sql.Timestamp value) (str value)
    (instance? java.math.BigDecimal value) (double value)
    :else value))

(defn- validation-error
  "Build a 400 validation error response."
  [message details]
  {:status 400
   :headers {"Content-Type" "application/json"}
   :body (json/write-str {:error {:code "VALIDATION_ERROR"
                                  :message message
                                  :details details}})})

(defn- not-found-error
  "Build a 404 not-found error response."
  [message]
  {:status 404
   :headers {"Content-Type" "application/json"}
   :body (json/write-str {:error {:code "NOT_FOUND"
                                  :message message}})})

(defn- insufficient-stock-error
  "Build a 409 insufficient stock error response."
  [sku available]
  {:status 409
   :headers {"Content-Type" "application/json"}
   :body (json/write-str {:error {:code "INSUFFICIENT_STOCK"
                                  :message "Requested quantity exceeds available stock"
                                  :details [{:field "quantity"
                                             :reason (str "Only " available " units available for SKU '" sku "'")}]}})})

(defn- compute-item
  "Compute subtotal for a cart item map."
  [item]
  (let [qty (:quantity item)
        price (double (:unit_price_snapshot item))
        subtotal (* qty price)]
    (assoc item :subtotal subtotal :unit_price_snapshot price)))

(defn- build-cart-response
  "Build the full cart response with items and computed total."
  [cart items]
  (let [computed-items (mapv compute-item items)
        total (reduce + 0.0 (map :subtotal computed-items))]
    {:id (str (:id cart))
     :status (:status cart)
     :items computed-items
     :total total
     :created_at (:created_at cart)
     :updated_at (:updated_at cart)}))

(defn- empty-cart-response
  "Return the empty cart response (no cart exists yet)."
  []
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str {:items [] :total 0})})

(defn- cart-json-response
  "Build a 200 JSON response from a cart and its items."
  [cart items]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str (build-cart-response cart items)
                         :value-fn json-value-fn)})

(defn get-cart
  "GET /api/cart handler. Returns the current session's cart.
   If no cart exists, returns empty cart {items: [], total: 0}."
  [datasource]
  (fn [request]
    (let [cart-id (:cart-id request)]
      (if-not cart-id
        (empty-cart-response)
        (let [cart (repo/find-cart-by-id datasource cart-id)]
          (if-not cart
            (empty-cart-response)
            (let [items (repo/get-cart-items datasource cart-id)]
              (cart-json-response cart items))))))))

(defn add-item
  "POST /api/cart/items handler. Adds an item to the cart.
   Creates the cart lazily on first add. Sets cookie via middleware metadata."
  [datasource]
  (fn [request]
    (let [body (:body-params request)
          product-sku (:product_sku body)
          quantity (:quantity body)]
      ;; Input validation
      (cond
        (or (nil? product-sku) (not (string? product-sku)) (empty? product-sku))
        (validation-error "Invalid request body"
                          [{:field "product_sku" :reason "product_sku is required and must be a non-empty string"}])

        (or (nil? quantity) (not (integer? quantity)) (< quantity 1))
        (validation-error "Invalid request body"
                          [{:field "quantity" :reason "quantity is required and must be a positive integer"}])

        :else
        (db/with-transaction [tx datasource]
          (let [product (repo/find-product-by-sku tx product-sku)]
            (if-not product
              (not-found-error (str "Product not found: " product-sku))
              ;; Determine cart-id (existing or new)
              (let [existing-cart-id (:cart-id request)
                    new-cart? (nil? existing-cart-id)
                    cart (if new-cart?
                           (repo/create-cart! tx)
                           (repo/find-cart-by-id tx existing-cart-id))
                    cart-id (:id cart)
                    ;; Check existing quantity in cart for this product
                    existing-item (when-not new-cart?
                                    (repo/find-cart-item tx cart-id product-sku))
                    existing-qty (or (:quantity existing-item) 0)
                    total-qty (+ existing-qty quantity)
                    stock (:stock product)]
                (if (> total-qty stock)
                  (insufficient-stock-error product-sku stock)
                  (do
                    ;; Upsert cart item with price snapshot
                    (repo/add-cart-item! tx cart-id product-sku quantity
                                         (:price product))
                    (repo/update-cart-timestamp! tx cart-id)
                    (let [items (repo/get-cart-items tx cart-id)
                          updated-cart (repo/find-cart-by-id tx cart-id)
                          response (cart-json-response updated-cart items)]
                      (if new-cart?
                        (with-meta response {:set-cart-cookie cart-id})
                        response))))))))))))

(defn update-item
  "PUT /api/cart/items/:sku handler. Updates the quantity of an item (absolute set)."
  [datasource]
  (fn [request]
    (let [cart-id (:cart-id request)
          sku (-> request :path-params :sku)
          body (:body-params request)
          quantity (:quantity body)]
      (cond
        (nil? cart-id)
        (not-found-error "Item not in cart")

        (or (nil? quantity) (not (integer? quantity)))
        (validation-error "Invalid request body"
                          [{:field "quantity" :reason "quantity is required and must be an integer"}])

        (<= quantity 0)
        (validation-error "Invalid request body"
                          [{:field "quantity" :reason "quantity must be greater than zero. Use DELETE to remove items"}])

        :else
        (db/with-transaction [tx datasource]
          (let [existing-item (repo/find-cart-item tx cart-id sku)]
            (if-not existing-item
              (not-found-error "Item not in cart")
              (let [product (repo/find-product-by-sku tx sku)
                    stock (:stock product)]
                (if (> quantity stock)
                  (insufficient-stock-error sku stock)
                  (do
                    (repo/update-cart-item-quantity! tx cart-id sku quantity)
                    (repo/update-cart-timestamp! tx cart-id)
                    (let [items (repo/get-cart-items tx cart-id)
                          updated-cart (repo/find-cart-by-id tx cart-id)]
                      (cart-json-response updated-cart items))))))))))))

(defn remove-item
  "DELETE /api/cart/items/:sku handler. Removes an item from the cart."
  [datasource]
  (fn [request]
    (let [cart-id (:cart-id request)
          sku (-> request :path-params :sku)]
      (if (nil? cart-id)
        (not-found-error "Item not in cart")
        (let [deleted? (repo/delete-cart-item! datasource cart-id sku)]
          (if-not deleted?
            (not-found-error "Item not in cart")
            (do
              (repo/update-cart-timestamp! datasource cart-id)
              (let [items (repo/get-cart-items datasource cart-id)
                    updated-cart (repo/find-cart-by-id datasource cart-id)]
                (cart-json-response updated-cart items)))))))))
