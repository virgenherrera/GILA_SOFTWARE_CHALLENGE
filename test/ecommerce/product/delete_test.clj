(ns ecommerce.product.delete-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [ecommerce.product.handler :as handler]
            [ecommerce.product.repository :as repo]))

(defn- parse-body
  "Parse JSON response body to a Clojure map with keyword keys."
  [response]
  (json/read-str (:body response) :key-fn keyword))

;; --- Delete handler unit tests ---

(deftest delete-returns-204-when-deleted-test
  (testing "Delete handler returns 204 with empty body when repository returns true"
    (with-redefs [repo/delete-product! (fn [_ds _sku] true)]
      (let [handler-fn (handler/delete-product :mock-ds)
            response (handler-fn {:path-params {:sku "RS-001"}})]
        (is (= 204 (:status response)))
        (is (nil? (:body response)))))))

(deftest delete-returns-404-when-not-found-test
  (testing "Delete handler returns 404 with NOT_FOUND when repository returns false"
    (with-redefs [repo/delete-product! (fn [_ds _sku] false)]
      (let [handler-fn (handler/delete-product :mock-ds)
            response (handler-fn {:path-params {:sku "NONEXISTENT"}})
            body (parse-body response)]
        (is (= 404 (:status response)))
        (is (= "NOT_FOUND" (get-in body [:error :code])))
        (is (= "Product not found" (get-in body [:error :message])))))))
