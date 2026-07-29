(ns ecommerce.product.handler-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]
            [ecommerce.router :as router]
            [ecommerce.product.repository :as repo]))

(defn- mock-datasource
  "Create a mock datasource (not connected to a real DB)."
  []
  (reify javax.sql.DataSource
    (getConnection [_]
      (throw (Exception. "Mock datasource - no real connection")))))

(defn- post-product
  "Create a POST /api/products request with JSON body."
  [handler body]
  (handler (-> (mock/request :post "/api/products")
               (mock/content-type "application/json")
               (mock/body (json/write-str body)))))

(defn- parse-body
  "Parse JSON response body to a Clojure map with keyword keys."
  [response]
  (json/read-str (:body response) :key-fn keyword))

;; --- Success ---

(deftest create-product-success-test
  (testing "Valid product creation returns 201 with correct body shape"
    (let [input {:sku "TEST-001"
                 :name "Test Widget"
                 :description "A test widget"
                 :price 29.99
                 :category "Electronics"
                 :stock 10}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/insert-product!
                    (fn [_ds product]
                      (assoc product
                             :created_at "2024-01-01T00:00:00Z"
                             :updated_at "2024-01-01T00:00:00Z"))]
        (let [response (post-product handler input)
              body (parse-body response)]
          (is (= 201 (:status response)))
          (is (= "TEST-001" (:sku body)))
          (is (= "Test Widget" (:name body)))
          (is (= "A test widget" (:description body)))
          (is (= 29.99 (:price body)))
          (is (= "Electronics" (:category body)))
          (is (= 10 (:stock body)))
          (is (contains? body :created_at))
          (is (contains? body :updated_at)))))))

;; --- Duplicate SKU ---

(deftest duplicate-sku-returns-409-test
  (testing "Duplicate SKU returns 409 with CONFLICT code"
    (let [input {:sku "DUPE-001"
                 :name "Duplicate Product"
                 :price 10.0
                 :category "Test"
                 :stock 1}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/insert-product!
                    (fn [_ds _product]
                      (throw (ex-info "A product with SKU 'DUPE-001' already exists"
                                      {:status 409
                                       :code "CONFLICT"
                                       :message "A product with SKU 'DUPE-001' already exists"})))]
        (let [response (post-product handler input)
              body (parse-body response)]
          (is (= 409 (:status response)))
          (is (= "CONFLICT" (get-in body [:error :code]))))))))

;; --- Validation errors ---

(deftest invalid-product-returns-400-test
  (testing "Invalid product returns 400 with VALIDATION_ERROR and details array"
    (let [input    {:sku ""
                    :name ""
                    :price -1.0
                    :category "Electronics"
                    :stock 10}
          handler  (router/create-router (mock-datasource))
          response (post-product handler input)
          body     (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code])))
      (is (= "Product validation failed" (get-in body [:error :message])))
      (is (vector? (get-in body [:error :details])))
      (is (pos? (count (get-in body [:error :details]))))
      ;; Each detail has :field and :reason
      (doseq [detail (get-in body [:error :details])]
        (is (contains? detail :field))
        (is (contains? detail :reason))))))

(deftest multi-error-returns-all-errors-test
  (testing "Multiple invalid fields return all errors in details array"
    (let [input    {:sku ""
                    :name ""
                    :price -1.0
                    :category ""
                    :stock -5}
          handler  (router/create-router (mock-datasource))
          response (post-product handler input)
          body     (parse-body response)
          details  (get-in body [:error :details])
          fields   (set (map :field details))]
      (is (= 400 (:status response)))
      (is (contains? fields "sku"))
      (is (contains? fields "name"))
      (is (contains? fields "price"))
      (is (contains? fields "category"))
      (is (contains? fields "stock")))))

;; --- XSS: store raw ---

(deftest xss-stored-as-is-test
  (testing "XSS payload in name is stored and returned as-is (not stripped)"
    (let [xss-name "<script>alert(1)</script>"
          input {:sku "XSS-001"
                 :name xss-name
                 :price 10.0
                 :category "Test"
                 :stock 1}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/insert-product!
                    (fn [_ds product]
                      (assoc product
                             :created_at "2024-01-01T00:00:00Z"
                             :updated_at "2024-01-01T00:00:00Z"))]
        (let [response (post-product handler input)
              body (parse-body response)]
          (is (= 201 (:status response)))
          (is (= xss-name (:name body))))))))

;; --- SQLi: stored as literal string ---

(deftest sqli-stored-as-literal-test
  (testing "SQL injection payload in name is stored as literal string"
    (let [sqli-name "'; DROP TABLE products;--"
          input {:sku "SQLI-001"
                 :name sqli-name
                 :price 10.0
                 :category "Test"
                 :stock 1}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/insert-product!
                    (fn [_ds product]
                      (assoc product
                             :created_at "2024-01-01T00:00:00Z"
                             :updated_at "2024-01-01T00:00:00Z"))]
        (let [response (post-product handler input)
              body (parse-body response)]
          (is (= 201 (:status response)))
          (is (= sqli-name (:name body))))))))

;; --- Name trimming ---

(deftest name-whitespace-trimmed-test
  (testing "Leading/trailing whitespace on name is trimmed before storage"
    (let [input {:sku "TRIM-001"
                 :name "  Padded Name  "
                 :price 10.0
                 :category "Test"
                 :stock 1}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/insert-product!
                    (fn [_ds product]
                      (is (= "Padded Name" (:name product))
                          "Name should be trimmed before insertion")
                      (assoc product
                             :created_at "2024-01-01T00:00:00Z"
                             :updated_at "2024-01-01T00:00:00Z"))]
        (let [response (post-product handler input)]
          (is (= 201 (:status response))))))))

(deftest whitespace-only-name-rejected-test
  (testing "Whitespace-only name is rejected after trim"
    (let [input    {:sku "WS-001"
                    :name "   "
                    :price 10.0
                    :category "Test"
                    :stock 1}
          handler  (router/create-router (mock-datasource))
          response (post-product handler input)
          body     (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code]))))))
