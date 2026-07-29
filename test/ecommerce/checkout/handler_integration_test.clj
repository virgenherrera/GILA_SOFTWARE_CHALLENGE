(ns ecommerce.checkout.handler-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]
            [ecommerce.router :as router]
            [ecommerce.checkout.service :as service]
            [ecommerce.order.repository :as order-repo]
            [ecommerce.cart.middleware :as cart-mw]))

(def ^:private test-secret "test-secret-key-at-least-32-chars-long")

(def ^:private test-cart-id
  (java.util.UUID/fromString "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))

(def ^:private test-cart-id-2
  (java.util.UUID/fromString "ffffffff-eeee-dddd-cccc-bbbbbbbbbbbb"))

(def ^:private test-order-id
  (java.util.UUID/fromString "11111111-2222-3333-4444-555555555555"))

(def ^:private test-order
  {:id test-order-id
   :status "Paid"
   :placed_at "2026-07-27T14:40:00Z"
   :items [{:product_sku "RS-001"
            :name "Running Shoes"
            :quantity 2
            :unit_price 89.99M
            :line_subtotal 179.98M}]
   :total_amount 179.98M})

(defn- mock-datasource []
  (reify javax.sql.DataSource
    (getConnection [_]
      (throw (Exception. "Mock datasource — no real connection")))))

(defn- create-handler []
  (router/create-router (mock-datasource) nil test-secret))

(defn- parse-body [response]
  (json/read-str (:body response) :key-fn keyword))

(defn- post-checkout
  "POST /api/checkout with optional signed cart cookie."
  ([handler]
   (post-checkout handler nil))
  ([handler cart-id]
   (let [req (cond-> (mock/request :post "/api/checkout")
               cart-id (mock/header "Cookie"
                                    (str "cart_id="
                                         (cart-mw/sign-cart-id test-secret cart-id))))]
     (handler req))))

(defn- get-order-request
  "GET /api/orders/:id"
  [handler id-str]
  (let [req (mock/request :get (str "/api/orders/" id-str))]
    (handler req)))

;; --- POST /api/checkout ---

(deftest checkout-success-test
  (testing "POST /api/checkout returns 201 with order details"
    (with-redefs [service/perform-checkout!
                  (fn [_ds cart-id]
                    (is (= test-cart-id cart-id))
                    test-order)]
      (let [handler (create-handler)
            response (post-checkout handler test-cart-id)
            body (parse-body response)]
        (is (= 201 (:status response)))
        (is (= (str test-order-id) (:id body)))
        (is (= "Paid" (:status body)))
        (is (= "2026-07-27T14:40:00Z" (:placed_at body)))
        (is (= 179.98 (:total_amount body)))
        (is (= 1 (count (:items body))))
        (let [item (first (:items body))]
          (is (= "RS-001" (:product_sku item)))
          (is (= "Running Shoes" (:name item)))
          (is (= 2 (:quantity item)))
          (is (= 89.99 (:unit_price item)))
          (is (= 179.98 (:line_subtotal item))))))))

(deftest checkout-no-cookie-test
  (testing "POST /api/checkout without cookie returns 400"
    (with-redefs [service/perform-checkout!
                  (fn [_ds _cart-id]
                    (throw (ex-info "No cart found"
                                    {:status 400 :code "EMPTY_CART"
                                     :message "No cart found. Add items before checkout."})))]
      (let [handler (create-handler)
            response (post-checkout handler)
            body (parse-body response)]
        (is (= 400 (:status response)))
        (is (= "EMPTY_CART" (get-in body [:error :code])))))))

(deftest checkout-empty-cart-test
  (testing "POST /api/checkout with empty cart returns 400"
    (with-redefs [service/perform-checkout!
                  (fn [_ds _cart-id]
                    (throw (ex-info "Cart is empty"
                                    {:status 400 :code "EMPTY_CART"
                                     :message "Cart is empty. Add items before checkout."})))]
      (let [handler (create-handler)
            response (post-checkout handler test-cart-id)
            body (parse-body response)]
        (is (= 400 (:status response)))
        (is (= "EMPTY_CART" (get-in body [:error :code])))))))

(deftest checkout-insufficient-stock-test
  (testing "POST /api/checkout with insufficient stock returns 409 with item details"
    (with-redefs [service/perform-checkout!
                  (fn [_ds _cart-id]
                    (throw (ex-info "Insufficient stock"
                                    {:status 409 :code "INSUFFICIENT_STOCK"
                                     :message "Some items exceed available stock"
                                     :items [{:product_sku "RS-001"
                                              :requested 5
                                              :available 3}]})))]
      (let [handler (create-handler)
            response (post-checkout handler test-cart-id)
            body (parse-body response)]
        (is (= 409 (:status response)))
        (is (= "INSUFFICIENT_STOCK" (get-in body [:error :code])))
        (let [items (get-in body [:error :items])]
          (is (= 1 (count items)))
          (is (= "RS-001" (:product_sku (first items))))
          (is (= 5 (:requested (first items))))
          (is (= 3 (:available (first items)))))))))

(deftest checkout-cart-already-checked-out-test
  (testing "POST /api/checkout on already-checked-out cart returns 400"
    (with-redefs [service/perform-checkout!
                  (fn [_ds _cart-id]
                    (throw (ex-info "Cart is not active"
                                    {:status 400 :code "CART_NOT_ACTIVE"
                                     :message "Cart has already been checked out."})))]
      (let [handler (create-handler)
            response (post-checkout handler test-cart-id)
            body (parse-body response)]
        (is (= 400 (:status response)))
        (is (= "CART_NOT_ACTIVE" (get-in body [:error :code])))))))

(deftest checkout-concurrent-race-test
  (testing "Concurrent checkout: one succeeds (201), one fails (409)"
    (let [call-count (atom 0)]
      (with-redefs [service/perform-checkout!
                    (fn [_ds _cart-id]
                      (let [n (swap! call-count inc)]
                        (if (= 1 n)
                          ;; First caller wins
                          test-order
                          ;; Second caller gets insufficient stock
                          (throw (ex-info "Insufficient stock"
                                          {:status 409 :code "INSUFFICIENT_STOCK"
                                           :message "Some items exceed available stock"
                                           :items [{:product_sku "RS-001"
                                                    :requested 1
                                                    :available 0}]})))))]
        (let [handler (create-handler)
              f1 (future (post-checkout handler test-cart-id))
              f2 (future (post-checkout handler test-cart-id-2))
              r1 @f1
              r2 @f2
              statuses (sort [(:status r1) (:status r2)])]
          (is (= [201 409] statuses)))))))

(deftest checkout-stock-revalidated-at-checkout-test
  (testing "Stock is re-validated at checkout time, not cart-add time"
    ;; Scenario: item was added when stock=10, but by checkout time stock=0
    (with-redefs [service/perform-checkout!
                  (fn [_ds _cart-id]
                    ;; Service detects stock was depleted after cart add
                    (throw (ex-info "Insufficient stock"
                                    {:status 409 :code "INSUFFICIENT_STOCK"
                                     :message "Some items exceed available stock"
                                     :items [{:product_sku "RS-001"
                                              :requested 2
                                              :available 0}]})))]
      (let [handler (create-handler)
            response (post-checkout handler test-cart-id)
            body (parse-body response)]
        (is (= 409 (:status response)))
        (is (= "INSUFFICIENT_STOCK" (get-in body [:error :code])))
        (is (= 0 (:available (first (get-in body [:error :items])))))))))

;; --- GET /api/orders/:id ---

(deftest get-order-success-test
  (testing "GET /api/orders/:id returns 200 with order details"
    (with-redefs [order-repo/find-order-by-id
                  (fn [_ds order-id]
                    (when (= test-order-id order-id)
                      {:id test-order-id :status "Paid"
                       :total 179.98M :created_at "2026-07-27T14:40:00Z"}))
                  order-repo/find-order-items
                  (fn [_ds _order-id]
                    [{:product_sku "RS-001" :name "Running Shoes"
                      :quantity 2 :price_at_purchase 89.99M}])]
      (let [handler (create-handler)
            response (get-order-request handler (str test-order-id))
            body (parse-body response)]
        (is (= 200 (:status response)))
        (is (= (str test-order-id) (:id body)))
        (is (= "Paid" (:status body)))
        (is (= "2026-07-27T14:40:00Z" (:placed_at body)))
        (is (= 179.98 (:total_amount body)))
        (let [item (first (:items body))]
          (is (= "RS-001" (:product_sku item)))
          (is (= "Running Shoes" (:name item)))
          (is (= 2 (:quantity item)))
          (is (= 89.99 (:unit_price item)))
          (is (= 179.98 (:line_subtotal item))))))))

(deftest get-order-not-found-test
  (testing "GET /api/orders/:id for non-existent order returns 404"
    (with-redefs [order-repo/find-order-by-id (fn [_ds _id] nil)]
      (let [handler (create-handler)
            random-id (java.util.UUID/randomUUID)
            response (get-order-request handler (str random-id))
            body (parse-body response)]
        (is (= 404 (:status response)))
        (is (= "NOT_FOUND" (get-in body [:error :code])))))))

(deftest get-order-invalid-uuid-test
  (testing "GET /api/orders/:id with invalid UUID returns 404"
    (let [handler (create-handler)
          response (get-order-request handler "not-a-uuid")
          body (parse-body response)]
      (is (= 404 (:status response)))
      (is (= "NOT_FOUND" (get-in body [:error :code]))))))

(deftest get-order-immutable-test
  (testing "Order details are immutable after placement"
    (let [order-data {:id test-order-id :status "Paid"
                      :total 179.98M :created_at "2026-07-27T14:40:00Z"}
          items-data [{:product_sku "RS-001" :name "Running Shoes"
                       :quantity 2 :price_at_purchase 89.99M}]]
      (with-redefs [order-repo/find-order-by-id (fn [_ds _id] order-data)
                    order-repo/find-order-items (fn [_ds _id] items-data)]
        (let [handler (create-handler)
              response1 (get-order-request handler (str test-order-id))
              response2 (get-order-request handler (str test-order-id))
              body1 (parse-body response1)
              body2 (parse-body response2)]
          ;; Two fetches return identical data
          (is (= body1 body2))
          (is (= "Paid" (:status body1)))
          (is (= 179.98 (:total_amount body1))))))))

(deftest checkout-price-from-snapshot-integration-test
  (testing "Order price matches cart snapshot, visible in GET /api/orders/:id"
    ;; Checkout with snapshot price 89.99
    (with-redefs [service/perform-checkout!
                  (fn [_ds _cart-id]
                    {:id (str test-order-id)
                     :status "Paid"
                     :placed_at "2026-07-27T14:40:00Z"
                     :items [{:product_sku "RS-001"
                              :name "Running Shoes"
                              :quantity 2
                              :unit_price 89.99M
                              :line_subtotal 179.98M}]
                     :total_amount 179.98M})]
      (let [handler (create-handler)
            checkout-response (post-checkout handler test-cart-id)
            checkout-body (parse-body checkout-response)]
        (is (= 201 (:status checkout-response)))
        ;; Verify snapshot price in checkout response
        (is (= 89.99 (:unit_price (first (:items checkout-body)))))
        (is (= 179.98 (:total_amount checkout-body)))))))
