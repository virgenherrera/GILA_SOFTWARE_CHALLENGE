(ns ecommerce.checkout.service
  (:require [ecommerce.db :as db]
            [ecommerce.cart.repository :as cart-repo]
            [ecommerce.order.repository :as order-repo]))

(defn perform-checkout!
  "Atomic checkout within a single transaction.
   Returns the created order map or throws ExceptionInfo on failure.

   Steps:
   1. Verify cart exists and is Active
   2. Get cart items
   3. Lock products with SELECT FOR UPDATE
   4. Validate stock for each item
   5. Decrement stock
   6. Calculate total (using snapshot prices)
   7. Create order (status='Paid')
   8. Create order_items
   9. Mark cart as CheckedOut
   10. Return order with items"
  [datasource cart-id]
  (when-not cart-id
    (throw (ex-info "No cart found"
                    {:status 400 :code "EMPTY_CART"
                     :message "No cart found. Add items before checkout."})))
  (db/with-transaction [tx datasource]
    ;; 1. Verify cart exists and is Active.
    ;; Locks the cart row (SELECT FOR UPDATE) so a second, concurrent
    ;; checkout for the same cart blocks until this transaction commits
    ;; and then observes the cart as no longer "Active".
    (let [cart (cart-repo/find-cart-by-id-for-update tx cart-id)]
      (when-not cart
        (throw (ex-info "Cart not found"
                        {:status 400 :code "EMPTY_CART"
                         :message "No cart found. Add items before checkout."})))
      (when (not= "Active" (:status cart))
        (throw (ex-info "Cart is not active"
                        {:status 400 :code "CART_NOT_ACTIVE"
                         :message "Cart has already been checked out."})))

      ;; 2. Get cart items
      (let [cart-items (cart-repo/get-cart-items tx cart-id)]
        (when (empty? cart-items)
          (throw (ex-info "Cart is empty"
                          {:status 400 :code "EMPTY_CART"
                           :message "Cart is empty. Add items before checkout."})))

        ;; 3. Lock products with SELECT FOR UPDATE
        ;; 4. Validate stock for each item
        (let [product-skus (mapv :product_sku cart-items)
              locked-products (order-repo/lock-products-for-update! tx product-skus)
              products-by-sku (into {} (map (juxt :sku identity)) locked-products)
              insufficient (reduce
                            (fn [acc item]
                              (let [product (get products-by-sku (:product_sku item))
                                    available (or (:stock product) 0)
                                    requested (:quantity item)]
                                (if (> requested available)
                                  (conj acc {:product_sku (:product_sku item)
                                             :requested requested
                                             :available available})
                                  acc)))
                            []
                            cart-items)]
          (when (seq insufficient)
            (throw (ex-info "Insufficient stock"
                            {:status 409 :code "INSUFFICIENT_STOCK"
                             :message "Some items exceed available stock"
                             :items insufficient})))

          ;; 5. Decrement stock for each product
          (doseq [item cart-items]
            (order-repo/decrement-product-stock!
             tx (:product_sku item) (:quantity item)))

          ;; 6. Calculate total (using snapshot prices from cart_items)
          ;; 7. Create order
          ;; 8. Create order_items (using snapshot prices)
          (let [total (reduce
                       (fn [sum item]
                         (+ sum (* (:quantity item)
                                   (bigdec (:unit_price_snapshot item)))))
                       0M
                       cart-items)
                order (order-repo/create-order! tx cart-id total)
                order-id (:id order)
                order-items (mapv
                             (fn [item]
                               (let [qty (:quantity item)
                                     price (bigdec (:unit_price_snapshot item))
                                     subtotal (* qty price)]
                                 (order-repo/create-order-item!
                                  tx order-id (:product_sku item) qty price)
                                 {:product_sku (:product_sku item)
                                  :name (:name item)
                                  :quantity qty
                                  :unit_price price
                                  :line_subtotal subtotal}))
                             cart-items)]

            ;; 9. Mark cart as CheckedOut
            (order-repo/update-cart-status! tx cart-id "CheckedOut")

            ;; 10. Return order with items
            {:id (str order-id)
             :status (:status order)
             :placed_at (:created_at order)
             :items order-items
             :total_amount (:total order)}))))))
