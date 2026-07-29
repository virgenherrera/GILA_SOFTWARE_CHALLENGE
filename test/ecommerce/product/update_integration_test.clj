(ns ecommerce.product.update-integration-test
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

(defn- put-product
  "Create a PUT /api/products/:sku request with JSON body."
  [handler sku body]
  (handler (-> (mock/request :put (str "/api/products/" sku))
               (mock/content-type "application/json")
               (mock/body (json/write-str body)))))

(defn- parse-body
  "Parse JSON response body to a Clojure map with keyword keys."
  [response]
  (json/read-str (:body response) :key-fn keyword))

;; --- Success ---

(deftest update-product-success-test
  (testing "Valid update returns 200 with updated product shape"
    (let [input {:name "Running Shoes v2"
                 :description "Updated description"
                 :price 94.99
                 :category "Footwear"
                 :stock 120}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/update-product!
                    (fn [_ds sku product]
                      (assoc product
                             :sku sku
                             :created_at "2024-01-01T00:00:00Z"
                             :updated_at "2024-06-15T12:00:00Z"))]
        (let [response (put-product handler "SHOE-001" input)
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= "SHOE-001" (:sku body)))
          (is (= "Running Shoes v2" (:name body)))
          (is (= "Updated description" (:description body)))
          (is (= 94.99 (:price body)))
          (is (= "Footwear" (:category body)))
          (is (= 120 (:stock body)))
          (is (contains? body :created_at))
          (is (contains? body :updated_at)))))))

;; --- Not Found ---

(deftest update-nonexistent-sku-returns-404-test
  (testing "Non-existent SKU returns 404 with NOT_FOUND"
    (let [input {:name "Ghost Product"
                 :price 10.0
                 :category "Test"
                 :stock 1}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/update-product!
                    (fn [_ds _sku _product] nil)]
        (let [response (put-product handler "NONEXISTENT" input)
              body (parse-body response)]
          (is (= 404 (:status response)))
          (is (= "NOT_FOUND" (get-in body [:error :code])))
          (is (= "Product not found" (get-in body [:error :message]))))))))

;; --- SKU mismatch ---

(deftest sku-mismatch-returns-400-test
  (testing "SKU in body different from URL param returns 400 with BAD_REQUEST"
    (let [input {:sku "DIFFERENT-SKU"
                 :name "Mismatch Product"
                 :price 10.0
                 :category "Test"
                 :stock 1}
          handler (router/create-router (mock-datasource))]
      (let [response (put-product handler "URL-SKU" input)
            body (parse-body response)]
        (is (= 400 (:status response)))
        (is (= "BAD_REQUEST" (get-in body [:error :code])))
        (is (= "SKU in body does not match URL parameter"
               (get-in body [:error :message])))))))

;; --- Validation errors ---

(deftest invalid-body-returns-400-test
  (testing "Invalid body returns 400 with VALIDATION_ERROR and details"
    (let [input {:name ""
                 :price -1.0
                 :category ""
                 :stock -5}
          handler (router/create-router (mock-datasource))
          response (put-product handler "TEST-001" input)
          body (parse-body response)
          details (get-in body [:error :details])
          fields (set (map :field details))]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code])))
      (is (= "Product validation failed" (get-in body [:error :message])))
      (is (vector? details))
      (is (pos? (count details)))
      (is (contains? fields "name"))
      (is (contains? fields "price"))
      (is (contains? fields "category"))
      (is (contains? fields "stock"))
      ;; Each detail has :field and :reason
      (doseq [detail details]
        (is (contains? detail :field))
        (is (contains? detail :reason))))))

;; --- XSS: store raw ---

(deftest xss-stored-as-is-test
  (testing "XSS payload in name is stored and returned as-is (not stripped)"
    (let [xss-name "<script>alert(1)</script>"
          input {:name xss-name
                 :price 10.0
                 :category "Test"
                 :stock 1}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/update-product!
                    (fn [_ds sku product]
                      (assoc product
                             :sku sku
                             :created_at "2024-01-01T00:00:00Z"
                             :updated_at "2024-06-15T12:00:00Z"))]
        (let [response (put-product handler "XSS-001" input)
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= xss-name (:name body))))))))

;; --- SQLi: stored as literal string ---

(deftest sqli-stored-as-literal-test
  (testing "SQL injection payload in name is stored as literal string"
    (let [sqli-name "'; DROP TABLE products;--"
          input {:name sqli-name
                 :price 10.0
                 :category "Test"
                 :stock 1}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/update-product!
                    (fn [_ds sku product]
                      (assoc product
                             :sku sku
                             :created_at "2024-01-01T00:00:00Z"
                             :updated_at "2024-06-15T12:00:00Z"))]
        (let [response (put-product handler "SQLI-001" input)
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= sqli-name (:name body))))))))

;; --- Name trimming ---

(deftest name-whitespace-trimmed-test
  (testing "Leading/trailing whitespace on name is trimmed before storage"
    (let [input {:name "  Padded Name  "
                 :price 10.0
                 :category "Test"
                 :stock 1}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/update-product!
                    (fn [_ds sku product]
                      (is (= "Padded Name" (:name product))
                          "Name should be trimmed before update")
                      (assoc product
                             :sku sku
                             :created_at "2024-01-01T00:00:00Z"
                             :updated_at "2024-06-15T12:00:00Z"))]
        (let [response (put-product handler "TRIM-001" input)]
          (is (= 200 (:status response))))))))

;; --- SKU match in body is allowed ---

(deftest sku-matching-url-is-allowed-test
  (testing "SKU in body matching URL param is allowed (stripped before validation)"
    (let [input {:sku "MATCH-001"
                 :name "Match Product"
                 :price 10.0
                 :category "Test"
                 :stock 1}
          handler (router/create-router (mock-datasource))]
      (with-redefs [repo/update-product!
                    (fn [_ds sku product]
                      (is (nil? (:sku product))
                          "SKU should be stripped from body params before update")
                      (assoc product
                             :sku sku
                             :created_at "2024-01-01T00:00:00Z"
                             :updated_at "2024-06-15T12:00:00Z"))]
        (let [response (put-product handler "MATCH-001" input)
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= "MATCH-001" (:sku body))))))))
