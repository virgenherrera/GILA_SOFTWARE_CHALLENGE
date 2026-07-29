(ns ecommerce.product.delete-integration-test
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

(defn- delete-product
  "Create a DELETE /api/products/:sku request."
  [handler sku]
  (handler (mock/request :delete (str "/api/products/" sku))))

(defn- parse-body
  "Parse JSON response body to a Clojure map with keyword keys."
  [response]
  (json/read-str (:body response) :key-fn keyword))

;; --- Successful delete ---

(deftest delete-product-success-test
  (testing "Successful delete returns 204 with empty body"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/delete-product! (fn [_ds _sku] true)]
        (let [response (delete-product handler "RS-001")]
          (is (= 204 (:status response)))
          (is (or (nil? (:body response))
                  (= "" (:body response)))))))))

;; --- Not Found ---

(deftest delete-nonexistent-sku-returns-404-test
  (testing "Non-existent SKU returns 404 with NOT_FOUND code"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/delete-product! (fn [_ds _sku] false)]
        (let [response (delete-product handler "NONEXISTENT")
              body (parse-body response)]
          (is (= 404 (:status response)))
          (is (= "NOT_FOUND" (get-in body [:error :code])))
          (is (= "Product not found" (get-in body [:error :message]))))))))

;; --- FK violation (PRODUCT_IN_USE) ---

(deftest delete-product-in-use-returns-409-test
  (testing "FK violation returns 409 with PRODUCT_IN_USE code"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/delete-product!
                    (fn [_ds sku]
                      (throw (ex-info (str "Cannot delete product '" sku "': referenced by existing orders or cart items")
                                      {:status 409
                                       :code "PRODUCT_IN_USE"
                                       :message (str "Cannot delete product '" sku "': referenced by existing orders or cart items")})))]
        (let [response (delete-product handler "RS-001")
              body (parse-body response)]
          (is (= 409 (:status response)))
          (is (= "PRODUCT_IN_USE" (get-in body [:error :code])))
          (is (string? (get-in body [:error :message]))))))))

;; --- No SQL internals leaked ---

(deftest delete-fk-error-does-not-leak-sql-details-test
  (testing "FK violation error body does not contain SQL state codes or constraint names"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/delete-product!
                    (fn [_ds sku]
                      (throw (ex-info (str "Cannot delete product '" sku "': referenced by existing orders or cart items")
                                      {:status 409
                                       :code "PRODUCT_IN_USE"
                                       :message (str "Cannot delete product '" sku "': referenced by existing orders or cart items")})))]
        (let [response (delete-product handler "RS-001")
              raw-body (:body response)]
          (is (not (re-find #"23503" raw-body))
              "Response body should not contain SQL state code 23503")
          (is (not (re-find #"(?i)fk_" raw-body))
              "Response body should not contain FK constraint names"))))))
