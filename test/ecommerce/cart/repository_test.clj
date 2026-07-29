(ns ecommerce.cart.repository-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [ecommerce.cart.repository :as repo]
            [next.jdbc :as jdbc]))

;; --- Query shape tests (verify HoneySQL generates correct SQL) ---

(deftest create-cart-calls-insert-test
  (testing "create-cart! calls JDBC with an INSERT INTO carts query"
    (let [captured (atom nil)]
      (with-redefs [jdbc/execute-one! (fn [_ds query _opts]
                                        (reset! captured query)
                                        {:id (java.util.UUID/randomUUID)
                                         :status "Active"
                                         :created_at "2024-01-01T00:00:00Z"
                                         :updated_at "2024-01-01T00:00:00Z"})]
        (let [result (repo/create-cart! :mock-ds)]
          (is (some? result))
          (is (some? (:id result)))
          (is (= "Active" (:status result)))
          ;; Verify the query starts with INSERT
          (is (str/starts-with? (first @captured) "INSERT INTO")))))))

(deftest find-cart-by-id-calls-select-test
  (testing "find-cart-by-id calls JDBC with a SELECT FROM carts query"
    (let [cart-id (java.util.UUID/randomUUID)
          captured (atom nil)]
      (with-redefs [jdbc/execute-one! (fn [_ds query _opts]
                                        (reset! captured query)
                                        {:id cart-id :status "Active"})]
        (let [result (repo/find-cart-by-id :mock-ds cart-id)]
          (is (= cart-id (:id result)))
          (is (str/starts-with? (first @captured) "SELECT")))))))

(deftest find-cart-item-calls-select-test
  (testing "find-cart-item calls JDBC with a SELECT and WHERE on cart_id and product_sku"
    (let [cart-id (java.util.UUID/randomUUID)
          captured (atom nil)]
      (with-redefs [jdbc/execute-one! (fn [_ds query _opts]
                                        (reset! captured query)
                                        {:id (java.util.UUID/randomUUID)
                                         :cart_id cart-id
                                         :product_sku "RS-001"
                                         :quantity 2
                                         :unit_price_snapshot 89.99M})]
        (let [result (repo/find-cart-item :mock-ds cart-id "RS-001")]
          (is (= "RS-001" (:product_sku result)))
          (is (= 2 (:quantity result)))
          ;; Query should reference both cart_id and product_sku
          (let [sql-str (first @captured)]
            (is (str/includes? sql-str "cart_id"))
            (is (str/includes? sql-str "product_sku"))))))))

(deftest add-cart-item-uses-on-conflict-test
  (testing "add-cart-item! generates an INSERT with ON CONFLICT DO UPDATE"
    (let [cart-id (java.util.UUID/randomUUID)
          captured (atom nil)]
      (with-redefs [jdbc/execute-one! (fn [_ds query _opts]
                                        (reset! captured query)
                                        {:id (java.util.UUID/randomUUID)
                                         :product_sku "RS-001"
                                         :quantity 2
                                         :unit_price_snapshot 89.99M})]
        (let [result (repo/add-cart-item! :mock-ds cart-id "RS-001" 2 89.99M)]
          (is (= "RS-001" (:product_sku result)))
          (let [sql-str (first @captured)]
            (is (str/includes? sql-str "INSERT INTO"))
            (is (str/includes? sql-str "ON CONFLICT"))))))))

(deftest update-cart-item-quantity-test
  (testing "update-cart-item-quantity! generates an UPDATE query"
    (let [cart-id (java.util.UUID/randomUUID)
          captured (atom nil)]
      (with-redefs [jdbc/execute-one! (fn [_ds query _opts]
                                        (reset! captured query)
                                        {:id (java.util.UUID/randomUUID)
                                         :product_sku "RS-001"
                                         :quantity 5
                                         :unit_price_snapshot 89.99M})]
        (let [result (repo/update-cart-item-quantity! :mock-ds cart-id "RS-001" 5)]
          (is (= 5 (:quantity result)))
          (is (str/starts-with? (first @captured) "UPDATE")))))))

(deftest delete-cart-item-returns-true-on-delete-test
  (testing "delete-cart-item! returns true when a row is deleted"
    (with-redefs [jdbc/execute-one! (fn [_ds _query]
                                      #:next.jdbc{:update-count 1})]
      (is (true? (repo/delete-cart-item! :mock-ds (java.util.UUID/randomUUID) "RS-001"))))))

(deftest delete-cart-item-returns-false-when-not-found-test
  (testing "delete-cart-item! returns false when no row matches"
    (with-redefs [jdbc/execute-one! (fn [_ds _query]
                                      #:next.jdbc{:update-count 0})]
      (is (false? (repo/delete-cart-item! :mock-ds (java.util.UUID/randomUUID) "NONEXISTENT"))))))

(deftest get-cart-items-joins-products-test
  (testing "get-cart-items generates a SELECT with JOIN on products"
    (let [cart-id (java.util.UUID/randomUUID)
          captured (atom nil)]
      (with-redefs [jdbc/execute! (fn [_ds query _opts]
                                    (reset! captured query)
                                    [{:product_sku "RS-001"
                                      :name "Running Shoes"
                                      :quantity 2
                                      :unit_price_snapshot 89.99M}])]
        (let [result (repo/get-cart-items :mock-ds cart-id)]
          (is (= 1 (count result)))
          (is (= "Running Shoes" (:name (first result))))
          (let [sql-str (first @captured)]
            (is (str/includes? sql-str "JOIN"))))))))

(deftest update-cart-timestamp-test
  (testing "update-cart-timestamp! generates an UPDATE on carts"
    (let [cart-id (java.util.UUID/randomUUID)
          captured (atom nil)]
      (with-redefs [jdbc/execute-one! (fn [_ds query]
                                        (reset! captured query)
                                        #:next.jdbc{:update-count 1})]
        (repo/update-cart-timestamp! :mock-ds cart-id)
        (let [sql-str (first @captured)]
          (is (str/starts-with? sql-str "UPDATE"))
          (is (str/includes? sql-str "carts")))))))

(deftest find-product-by-sku-test
  (testing "find-product-by-sku generates a SELECT from products"
    (let [captured (atom nil)]
      (with-redefs [jdbc/execute-one! (fn [_ds query _opts]
                                        (reset! captured query)
                                        {:sku "RS-001" :name "Running Shoes"
                                         :price 89.99M :stock 10})]
        (let [result (repo/find-product-by-sku :mock-ds "RS-001")]
          (is (= "RS-001" (:sku result)))
          (is (= 10 (:stock result)))
          (is (str/includes? (first @captured) "products")))))))
