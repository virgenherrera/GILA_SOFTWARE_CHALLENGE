(ns ecommerce.checkout.service-test
  (:require [clojure.test :refer [deftest is testing]]
            [ecommerce.checkout.service :as service]
            [ecommerce.cart.repository :as cart-repo]
            [ecommerce.order.repository :as order-repo]
            [next.jdbc.protocols :as jdbc-proto]))

;; --- Mock datasource that bypasses real DB transactions ---

(deftype MockTransactableDS []
  javax.sql.DataSource
  (getConnection [_]
    (throw (Exception. "Mock datasource — no real connection")))
  jdbc-proto/Sourceable
  (get-datasource [this] this)
  jdbc-proto/Transactable
  (-transact [this body-fn _opts]
    (body-fn this)))

(defn- mock-datasource []
  (MockTransactableDS.))

;; --- Test data ---

(def ^:private test-cart-id
  (java.util.UUID/fromString "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))

(def ^:private test-order-id
  (java.util.UUID/fromString "11111111-2222-3333-4444-555555555555"))

(def ^:private active-cart
  {:id test-cart-id :status "Active"})

(def ^:private checked-out-cart
  {:id test-cart-id :status "CheckedOut"})

(def ^:private test-cart-items
  [{:product_sku "RS-001"
    :name "Running Shoes"
    :quantity 2
    :unit_price_snapshot 89.99M}
   {:product_sku "TS-001"
    :name "T-Shirt"
    :quantity 1
    :unit_price_snapshot 29.99M}])

(def ^:private test-locked-products
  [{:sku "RS-001" :name "Running Shoes" :price 89.99M :stock 10}
   {:sku "TS-001" :name "T-Shirt" :price 29.99M :stock 5}])

;; --- Tests ---

(deftest checkout-no-cart-id-test
  (testing "Checkout with nil cart-id throws 400 EMPTY_CART"
    (let [ds (mock-datasource)]
      (try
        (service/perform-checkout! ds nil)
        (is false "Should have thrown")
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (is (= 400 (:status data)))
            (is (= "EMPTY_CART" (:code data)))))))))

(deftest checkout-cart-not-found-test
  (testing "Checkout with nonexistent cart throws 400 EMPTY_CART"
    (with-redefs [cart-repo/find-cart-by-id-for-update (fn [_ds _id] nil)]
      (let [ds (mock-datasource)]
        (try
          (service/perform-checkout! ds test-cart-id)
          (is false "Should have thrown")
          (catch clojure.lang.ExceptionInfo e
            (let [data (ex-data e)]
              (is (= 400 (:status data)))
              (is (= "EMPTY_CART" (:code data))))))))))

(deftest checkout-cart-already-checked-out-test
  (testing "Checkout with already-checked-out cart throws 400 CART_NOT_ACTIVE"
    (with-redefs [cart-repo/find-cart-by-id-for-update (fn [_ds _id] checked-out-cart)]
      (let [ds (mock-datasource)]
        (try
          (service/perform-checkout! ds test-cart-id)
          (is false "Should have thrown")
          (catch clojure.lang.ExceptionInfo e
            (let [data (ex-data e)]
              (is (= 400 (:status data)))
              (is (= "CART_NOT_ACTIVE" (:code data))))))))))

(deftest checkout-empty-cart-test
  (testing "Checkout with empty cart throws 400 EMPTY_CART"
    (with-redefs [cart-repo/find-cart-by-id-for-update (fn [_ds _id] active-cart)
                  cart-repo/get-cart-items (fn [_ds _id] [])]
      (let [ds (mock-datasource)]
        (try
          (service/perform-checkout! ds test-cart-id)
          (is false "Should have thrown")
          (catch clojure.lang.ExceptionInfo e
            (let [data (ex-data e)]
              (is (= 400 (:status data)))
              (is (= "EMPTY_CART" (:code data))))))))))

(deftest checkout-insufficient-stock-test
  (testing "Checkout with insufficient stock throws 409 with per-item details"
    (with-redefs [cart-repo/find-cart-by-id-for-update (fn [_ds _id] active-cart)
                  cart-repo/get-cart-items
                  (fn [_ds _id]
                    [{:product_sku "RS-001" :name "Running Shoes"
                      :quantity 5 :unit_price_snapshot 89.99M}])
                  order-repo/lock-products-for-update!
                  (fn [_tx _skus]
                    [{:sku "RS-001" :name "Running Shoes"
                      :price 89.99M :stock 3}])]
      (let [ds (mock-datasource)]
        (try
          (service/perform-checkout! ds test-cart-id)
          (is false "Should have thrown")
          (catch clojure.lang.ExceptionInfo e
            (let [data (ex-data e)
                  items (:items data)]
              (is (= 409 (:status data)))
              (is (= "INSUFFICIENT_STOCK" (:code data)))
              (is (= 1 (count items)))
              (is (= "RS-001" (:product_sku (first items))))
              (is (= 5 (:requested (first items))))
              (is (= 3 (:available (first items)))))))))))

(deftest checkout-insufficient-stock-multiple-items-test
  (testing "Checkout reports ALL items with insufficient stock, not just the first"
    (with-redefs [cart-repo/find-cart-by-id-for-update (fn [_ds _id] active-cart)
                  cart-repo/get-cart-items
                  (fn [_ds _id]
                    [{:product_sku "RS-001" :name "Running Shoes"
                      :quantity 10 :unit_price_snapshot 89.99M}
                     {:product_sku "TS-001" :name "T-Shirt"
                      :quantity 8 :unit_price_snapshot 29.99M}])
                  order-repo/lock-products-for-update!
                  (fn [_tx _skus]
                    [{:sku "RS-001" :name "Running Shoes"
                      :price 89.99M :stock 3}
                     {:sku "TS-001" :name "T-Shirt"
                      :price 29.99M :stock 2}])]
      (let [ds (mock-datasource)]
        (try
          (service/perform-checkout! ds test-cart-id)
          (is false "Should have thrown")
          (catch clojure.lang.ExceptionInfo e
            (let [items (:items (ex-data e))]
              (is (= 2 (count items)))
              (is (= #{"RS-001" "TS-001"}
                     (set (map :product_sku items)))))))))))

(deftest checkout-atomic-rollback-test
  (testing "If stock check fails, no stock is decremented (atomic rollback)"
    (let [decrement-calls (atom [])]
      (with-redefs [cart-repo/find-cart-by-id-for-update (fn [_ds _id] active-cart)
                    cart-repo/get-cart-items
                    (fn [_ds _id]
                      [{:product_sku "RS-001" :name "Running Shoes"
                        :quantity 2 :unit_price_snapshot 89.99M}
                       {:product_sku "TS-001" :name "T-Shirt"
                        :quantity 10 :unit_price_snapshot 29.99M}])
                    order-repo/lock-products-for-update!
                    (fn [_tx _skus]
                      [{:sku "RS-001" :name "Running Shoes"
                        :price 89.99M :stock 5}
                       {:sku "TS-001" :name "T-Shirt"
                        :price 29.99M :stock 3}])
                    order-repo/decrement-product-stock!
                    (fn [_tx sku qty]
                      (swap! decrement-calls conj {:sku sku :qty qty}))]
        (let [ds (mock-datasource)]
          (try
            (service/perform-checkout! ds test-cart-id)
            (is false "Should have thrown")
            (catch clojure.lang.ExceptionInfo _
              ;; Stock validation happens BEFORE decrement, so no decrements
              (is (empty? @decrement-calls)
                  "No stock should be decremented when validation fails"))))))))

(deftest checkout-success-test
  (testing "Successful checkout returns order with correct data"
    (let [decremented (atom [])
          created-items (atom [])
          cart-status (atom nil)]
      (with-redefs [cart-repo/find-cart-by-id-for-update (fn [_ds _id] active-cart)
                    cart-repo/get-cart-items (fn [_ds _id] test-cart-items)
                    order-repo/lock-products-for-update!
                    (fn [_tx _skus] test-locked-products)
                    order-repo/decrement-product-stock!
                    (fn [_tx sku qty]
                      (swap! decremented conj {:sku sku :qty qty}))
                    order-repo/create-order!
                    (fn [_tx _cart-id total]
                      {:id test-order-id :status "Paid"
                       :total total :created_at "2026-07-27T14:40:00Z"})
                    order-repo/create-order-item!
                    (fn [_tx _order-id sku qty price]
                      (swap! created-items conj {:sku sku :qty qty :price price})
                      {:id (java.util.UUID/randomUUID)
                       :product_sku sku :quantity qty
                       :price_at_purchase price})
                    order-repo/update-cart-status!
                    (fn [_tx _cart-id status]
                      (reset! cart-status status))]
        (let [ds (mock-datasource)
              result (service/perform-checkout! ds test-cart-id)]
          ;; Order fields
          (is (= (str test-order-id) (:id result)))
          (is (= "Paid" (:status result)))
          (is (some? (:placed_at result)))

          ;; Items
          (is (= 2 (count (:items result))))
          (let [item1 (first (:items result))
                item2 (second (:items result))]
            (is (= "RS-001" (:product_sku item1)))
            (is (= "Running Shoes" (:name item1)))
            (is (= 2 (:quantity item1)))
            (is (= 89.99M (:unit_price item1)))
            (is (= 179.98M (:line_subtotal item1)))

            (is (= "TS-001" (:product_sku item2)))
            (is (= 1 (:quantity item2)))
            (is (= 29.99M (:unit_price item2)))
            (is (= 29.99M (:line_subtotal item2))))

          ;; Total = 179.98 + 29.99 = 209.97
          (is (= 209.97M (:total_amount result)))

          ;; Stock decremented correctly
          (is (= 2 (count @decremented)))
          (is (= {:sku "RS-001" :qty 2} (first @decremented)))
          (is (= {:sku "TS-001" :qty 1} (second @decremented)))

          ;; Order items created
          (is (= 2 (count @created-items)))

          ;; Cart marked as CheckedOut
          (is (= "CheckedOut" @cart-status)))))))

(deftest checkout-uses-snapshot-price-test
  (testing "Order uses cart snapshot price, not current product price"
    (with-redefs [cart-repo/find-cart-by-id-for-update (fn [_ds _id] active-cart)
                  cart-repo/get-cart-items
                  (fn [_ds _id]
                    ;; Snapshot price: $89.99
                    [{:product_sku "RS-001" :name "Running Shoes"
                      :quantity 1 :unit_price_snapshot 89.99M}])
                  order-repo/lock-products-for-update!
                  (fn [_tx _skus]
                    ;; Current product price: $99.99 (changed since add-to-cart)
                    [{:sku "RS-001" :name "Running Shoes"
                      :price 99.99M :stock 10}])
                  order-repo/decrement-product-stock! (fn [_tx _sku _qty] nil)
                  order-repo/create-order!
                  (fn [_tx _cart-id total]
                    {:id test-order-id :status "Paid"
                     :total total :created_at "2026-07-27T14:40:00Z"})
                  order-repo/create-order-item!
                  (fn [_tx _oid _sku _qty _price]
                    {:id (java.util.UUID/randomUUID)})
                  order-repo/update-cart-status! (fn [_tx _cid _s] nil)]
      (let [ds (mock-datasource)
            result (service/perform-checkout! ds test-cart-id)
            item (first (:items result))]
        ;; Unit price should be the SNAPSHOT price (89.99), not current (99.99)
        (is (= 89.99M (:unit_price item)))
        (is (= 89.99M (:line_subtotal item)))
        ;; Total should also use snapshot price
        (is (= 89.99M (:total_amount result)))))))
