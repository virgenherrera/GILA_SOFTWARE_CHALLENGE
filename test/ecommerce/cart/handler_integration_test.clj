(ns ecommerce.cart.handler-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [ring.mock.request :as mock]
            [ecommerce.router :as router]
            [ecommerce.cart.repository :as cart-repo]
            [ecommerce.cart.middleware :as cart-mw]))

(def ^:private test-secret "test-secret-key-at-least-32-chars-long")

(def ^:private test-cart-id (java.util.UUID/fromString "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))

(def ^:private test-cart
  {:id test-cart-id
   :status "Active"
   :created_at "2024-01-01T00:00:00Z"
   :updated_at "2024-01-01T00:00:00Z"})

(def ^:private test-product
  {:sku "RS-001"
   :name "Running Shoes"
   :price 89.99M
   :stock 10})

(defn- mock-datasource []
  (reify javax.sql.DataSource
    (getConnection [_]
      (throw (Exception. "Mock datasource - no real connection")))))

(defn- create-handler []
  (router/create-router (mock-datasource) nil test-secret))

(defn- parse-body [response]
  (json/read-str (:body response) :key-fn keyword))

(defn- post-cart-item
  "POST /api/cart/items with JSON body, optionally with a signed cart cookie."
  ([handler body]
   (post-cart-item handler body nil))
  ([handler body cart-id]
   (let [req (cond-> (-> (mock/request :post "/api/cart/items")
                         (mock/content-type "application/json")
                         (mock/body (json/write-str body)))
               cart-id (mock/header "Cookie" (str "cart_id=" (cart-mw/sign-cart-id test-secret cart-id))))]
     (handler req))))

(defn- get-cart
  "GET /api/cart, optionally with a signed cart cookie."
  ([handler]
   (get-cart handler nil))
  ([handler cart-id]
   (let [req (cond-> (mock/request :get "/api/cart")
               cart-id (mock/header "Cookie" (str "cart_id=" (cart-mw/sign-cart-id test-secret cart-id))))]
     (handler req))))

(defn- put-cart-item
  "PUT /api/cart/items/:sku with JSON body and signed cart cookie."
  [handler sku body cart-id]
  (let [req (cond-> (-> (mock/request :put (str "/api/cart/items/" sku))
                        (mock/content-type "application/json")
                        (mock/body (json/write-str body)))
              cart-id (mock/header "Cookie" (str "cart_id=" (cart-mw/sign-cart-id test-secret cart-id))))]
    (handler req)))

(defn- delete-cart-item
  "DELETE /api/cart/items/:sku with signed cart cookie."
  [handler sku cart-id]
  (let [req (cond-> (mock/request :delete (str "/api/cart/items/" sku))
              cart-id (mock/header "Cookie" (str "cart_id=" (cart-mw/sign-cart-id test-secret cart-id))))]
    (handler req)))

;; --- GET /api/cart ---

(deftest get-empty-cart-no-cookie-test
  (testing "GET /api/cart without cookie returns empty cart"
    (let [handler (create-handler)
          response (get-cart handler)
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= [] (:items body)))
      (is (= 0 (:total body)))
      (is (not (contains? body :id)))
      (is (not (contains? body :status))))))

(deftest get-cart-with-items-test
  (testing "GET /api/cart with valid cookie returns cart with items and computed total"
    (with-redefs [cart-repo/find-cart-by-id (fn [_ds _id] test-cart)
                  cart-repo/get-cart-items (fn [_ds _id]
                                             [{:product_sku "RS-001"
                                               :name "Running Shoes"
                                               :quantity 2
                                               :unit_price_snapshot 89.99M}])]
      (let [handler (create-handler)
            response (get-cart handler test-cart-id)
            body (parse-body response)]
        (is (= 200 (:status response)))
        (is (= (str test-cart-id) (:id body)))
        (is (= "Active" (:status body)))
        (is (= 1 (count (:items body))))
        (let [item (first (:items body))]
          (is (= "RS-001" (:product_sku item)))
          (is (= "Running Shoes" (:name item)))
          (is (= 2 (:quantity item)))
          (is (= 89.99 (:unit_price_snapshot item)))
          (is (= 179.98 (:subtotal item))))
        (is (= 179.98 (:total body)))))))

;; --- POST /api/cart/items ---

(deftest add-item-creates-cart-and-sets-cookie-test
  (testing "POST /api/cart/items without existing cart creates cart and sets cookie"
    (with-redefs [cart-repo/find-product-by-sku (fn [_ds _sku] test-product)
                  cart-repo/create-cart! (fn [_ds] test-cart)
                  cart-repo/add-cart-item! (fn [_ds _cid _sku _qty _price] nil)
                  cart-repo/update-cart-timestamp! (fn [_ds _id] nil)
                  cart-repo/get-cart-items (fn [_ds _id]
                                             [{:product_sku "RS-001"
                                               :name "Running Shoes"
                                               :quantity 2
                                               :unit_price_snapshot 89.99M}])
                  cart-repo/find-cart-by-id (fn [_ds _id] test-cart)]
      (let [handler (create-handler)
            response (post-cart-item handler {:product_sku "RS-001" :quantity 2})
            body (parse-body response)]
        (is (= 200 (:status response)))
        (is (some? (get-in response [:headers "Set-Cookie"])))
        (is (str/includes? (get-in response [:headers "Set-Cookie"]) "cart_id="))
        (is (str/includes? (get-in response [:headers "Set-Cookie"]) "HttpOnly"))
        (is (str/includes? (get-in response [:headers "Set-Cookie"]) "SameSite=Strict"))
        (is (str/includes? (get-in response [:headers "Set-Cookie"]) "Path=/api"))
        (is (= (str test-cart-id) (:id body)))
        (is (= 1 (count (:items body))))))))

(deftest add-item-increments-quantity-test
  (testing "POST /api/cart/items for existing product increments quantity"
    (with-redefs [cart-repo/find-product-by-sku (fn [_ds _sku] test-product)
                  cart-repo/find-cart-by-id (fn [_ds _id] test-cart)
                  cart-repo/find-cart-item (fn [_ds _cid _sku]
                                             {:quantity 2 :unit_price_snapshot 89.99M})
                  cart-repo/add-cart-item! (fn [_ds _cid _sku _qty _price] nil)
                  cart-repo/update-cart-timestamp! (fn [_ds _id] nil)
                  cart-repo/get-cart-items (fn [_ds _id]
                                             [{:product_sku "RS-001"
                                               :name "Running Shoes"
                                               :quantity 5
                                               :unit_price_snapshot 89.99M}])]
      (let [handler (create-handler)
            response (post-cart-item handler {:product_sku "RS-001" :quantity 3} test-cart-id)
            body (parse-body response)]
        (is (= 200 (:status response)))
        ;; No Set-Cookie for existing cart
        (is (nil? (get-in response [:headers "Set-Cookie"])))
        (is (= 5 (:quantity (first (:items body)))))))))

(deftest add-item-stock-exceeded-test
  (testing "POST /api/cart/items with qty > stock returns 409 INSUFFICIENT_STOCK"
    (with-redefs [cart-repo/find-product-by-sku (fn [_ds _sku]
                                                  {:sku "RS-001" :name "Running Shoes"
                                                   :price 89.99M :stock 5})
                  cart-repo/find-cart-by-id (fn [_ds _id] test-cart)
                  cart-repo/find-cart-item (fn [_ds _cid _sku]
                                             {:quantity 3 :unit_price_snapshot 89.99M})]
      (let [handler (create-handler)
            response (post-cart-item handler {:product_sku "RS-001" :quantity 5} test-cart-id)
            body (parse-body response)]
        (is (= 409 (:status response)))
        (is (= "INSUFFICIENT_STOCK" (get-in body [:error :code])))
        (is (= "Requested quantity exceeds available stock" (get-in body [:error :message])))
        (is (str/includes?
             (get-in (first (get-in body [:error :details])) [:reason])
             "Only 5 units available"))))))

(deftest add-item-product-not-found-test
  (testing "POST /api/cart/items for non-existent product returns 404"
    (with-redefs [cart-repo/find-product-by-sku (fn [_ds _sku] nil)]
      (let [handler (create-handler)
            response (post-cart-item handler {:product_sku "NONEXISTENT" :quantity 1})
            body (parse-body response)]
        (is (= 404 (:status response)))
        (is (= "NOT_FOUND" (get-in body [:error :code])))))))

(deftest add-item-validation-missing-sku-test
  (testing "POST /api/cart/items with missing product_sku returns 400"
    (let [handler (create-handler)
          response (post-cart-item handler {:quantity 1})
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code]))))))

(deftest add-item-validation-invalid-quantity-test
  (testing "POST /api/cart/items with zero quantity returns 400"
    (let [handler (create-handler)
          response (post-cart-item handler {:product_sku "RS-001" :quantity 0})
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code]))))))

(deftest add-item-validation-negative-quantity-test
  (testing "POST /api/cart/items with negative quantity returns 400"
    (let [handler (create-handler)
          response (post-cart-item handler {:product_sku "RS-001" :quantity -3})
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code]))))))

;; --- PUT /api/cart/items/:sku ---

(deftest update-item-success-test
  (testing "PUT /api/cart/items/:sku with valid qty updates and returns cart"
    (with-redefs [cart-repo/find-cart-item (fn [_ds _cid _sku]
                                             {:quantity 2 :unit_price_snapshot 89.99M})
                  cart-repo/find-product-by-sku (fn [_ds _sku] test-product)
                  cart-repo/update-cart-item-quantity! (fn [_ds _cid _sku _qty] nil)
                  cart-repo/update-cart-timestamp! (fn [_ds _id] nil)
                  cart-repo/get-cart-items (fn [_ds _id]
                                             [{:product_sku "RS-001"
                                               :name "Running Shoes"
                                               :quantity 3
                                               :unit_price_snapshot 89.99M}])
                  cart-repo/find-cart-by-id (fn [_ds _id] test-cart)]
      (let [handler (create-handler)
            response (put-cart-item handler "RS-001" {:quantity 3} test-cart-id)
            body (parse-body response)]
        (is (= 200 (:status response)))
        (is (= 3 (:quantity (first (:items body)))))))))

(deftest update-item-zero-qty-rejected-test
  (testing "PUT /api/cart/items/:sku with qty=0 returns 400"
    (let [handler (create-handler)
          response (put-cart-item handler "RS-001" {:quantity 0} test-cart-id)
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code])))
      (is (str/includes?
           (get-in (first (get-in body [:error :details])) [:reason])
           "greater than zero")))))

(deftest update-item-negative-qty-rejected-test
  (testing "PUT /api/cart/items/:sku with negative qty returns 400"
    (let [handler (create-handler)
          response (put-cart-item handler "RS-001" {:quantity -1} test-cart-id)
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code]))))))

(deftest update-item-stock-exceeded-test
  (testing "PUT /api/cart/items/:sku with qty > stock returns 409"
    (with-redefs [cart-repo/find-cart-item (fn [_ds _cid _sku]
                                             {:quantity 2 :unit_price_snapshot 89.99M})
                  cart-repo/find-product-by-sku (fn [_ds _sku]
                                                  {:sku "RS-001" :name "Running Shoes"
                                                   :price 89.99M :stock 5})]
      (let [handler (create-handler)
            response (put-cart-item handler "RS-001" {:quantity 10} test-cart-id)
            body (parse-body response)]
        (is (= 409 (:status response)))
        (is (= "INSUFFICIENT_STOCK" (get-in body [:error :code])))))))

(deftest update-item-not-in-cart-test
  (testing "PUT /api/cart/items/:sku for item not in cart returns 404"
    (with-redefs [cart-repo/find-cart-item (fn [_ds _cid _sku] nil)]
      (let [handler (create-handler)
            response (put-cart-item handler "NONEXISTENT" {:quantity 1} test-cart-id)
            body (parse-body response)]
        (is (= 404 (:status response)))
        (is (= "NOT_FOUND" (get-in body [:error :code])))))))

(deftest update-item-no-cart-returns-404-test
  (testing "PUT /api/cart/items/:sku without cart returns 404"
    (let [handler (create-handler)
          response (put-cart-item handler "RS-001" {:quantity 1} nil)
          body (parse-body response)]
      (is (= 404 (:status response)))
      (is (= "NOT_FOUND" (get-in body [:error :code]))))))

;; --- DELETE /api/cart/items/:sku ---

(deftest delete-item-success-test
  (testing "DELETE /api/cart/items/:sku removes item and returns updated cart"
    (with-redefs [cart-repo/delete-cart-item! (fn [_ds _cid _sku] true)
                  cart-repo/update-cart-timestamp! (fn [_ds _id] nil)
                  cart-repo/get-cart-items (fn [_ds _id] [])
                  cart-repo/find-cart-by-id (fn [_ds _id] test-cart)]
      (let [handler (create-handler)
            response (delete-cart-item handler "RS-001" test-cart-id)
            body (parse-body response)]
        (is (= 200 (:status response)))
        (is (= [] (:items body)))
        (is (= 0.0 (:total body)))))))

(deftest delete-item-not-found-test
  (testing "DELETE /api/cart/items/:sku for non-existent item returns 404"
    (with-redefs [cart-repo/delete-cart-item! (fn [_ds _cid _sku] false)]
      (let [handler (create-handler)
            response (delete-cart-item handler "NONEXISTENT" test-cart-id)
            body (parse-body response)]
        (is (= 404 (:status response)))
        (is (= "NOT_FOUND" (get-in body [:error :code])))))))

(deftest delete-item-no-cart-returns-404-test
  (testing "DELETE /api/cart/items/:sku without cart returns 404"
    (let [handler (create-handler)
          response (delete-cart-item handler "RS-001" nil)
          body (parse-body response)]
      (is (= 404 (:status response)))
      (is (= "NOT_FOUND" (get-in body [:error :code]))))))

;; --- Subtotal calculation ---

(deftest subtotal-calculation-test
  (testing "Subtotal per item and total are computed correctly"
    (with-redefs [cart-repo/find-cart-by-id (fn [_ds _id] test-cart)
                  cart-repo/get-cart-items (fn [_ds _id]
                                             [{:product_sku "RS-001"
                                               :name "Running Shoes"
                                               :quantity 2
                                               :unit_price_snapshot 89.99M}
                                              {:product_sku "TS-001"
                                               :name "T-Shirt"
                                               :quantity 3
                                               :unit_price_snapshot 29.99M}])]
      (let [handler (create-handler)
            response (get-cart handler test-cart-id)
            body (parse-body response)
            items (:items body)]
        (is (= 2 (count items)))
        (is (= 179.98 (:subtotal (first items))))
        (is (= 89.97 (:subtotal (second items))))
        (is (= 269.95 (:total body)))))))

;; --- Price snapshot immunity ---

(deftest price-snapshot-immunity-test
  (testing "Price snapshot is immune to product price changes"
    (let [original-price 89.99M
          ;; Simulate: product was added at $89.99, but product price changed to $99.99
          changed-product {:sku "RS-001" :name "Running Shoes" :price 99.99M :stock 10}]
      (with-redefs [cart-repo/find-cart-by-id (fn [_ds _id] test-cart)
                    cart-repo/get-cart-items (fn [_ds _id]
                                               [{:product_sku "RS-001"
                                                 :name "Running Shoes"
                                                 :quantity 2
                                                ;; Snapshot still at original price
                                                 :unit_price_snapshot original-price}])
                    cart-repo/find-product-by-sku (fn [_ds _sku] changed-product)]
        (let [handler (create-handler)
              response (get-cart handler test-cart-id)
              body (parse-body response)
              item (first (:items body))]
          ;; Cart shows the ORIGINAL snapshot price, not the changed price
          (is (= 89.99 (:unit_price_snapshot item)))
          (is (= 179.98 (:subtotal item)))
          (is (= 179.98 (:total body))))))))

;; --- Cookie signing: tampered cookie ---

(deftest tampered-cookie-treated-as-no-cart-test
  (testing "A tampered cookie is treated as no cart (returns empty cart)"
    (let [handler (create-handler)
          req (-> (mock/request :get "/api/cart")
                  (mock/header "Cookie" "cart_id=tampered-invalid-token"))
          response (handler req)
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= [] (:items body)))
      (is (= 0 (:total body))))))

(deftest valid-signed-cookie-works-test
  (testing "A valid signed cookie is properly parsed"
    (with-redefs [cart-repo/find-cart-by-id (fn [_ds id]
                                              (when (= test-cart-id id) test-cart))
                  cart-repo/get-cart-items (fn [_ds _id] [])]
      (let [handler (create-handler)
            signed-token (cart-mw/sign-cart-id test-secret test-cart-id)
            req (-> (mock/request :get "/api/cart")
                    (mock/header "Cookie" (str "cart_id=" signed-token)))
            response (handler req)
            body (parse-body response)]
        (is (= 200 (:status response)))
        (is (= (str test-cart-id) (:id body)))
        (is (= "Active" (:status body)))))))
