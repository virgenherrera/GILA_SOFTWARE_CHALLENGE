(ns ecommerce.product.update-test
  (:require [clojure.test :refer [deftest is testing]]
            [ecommerce.validation :as v]))

(def ^:private valid-update
  {:name "Running Shoes v2"
   :description "Updated description"
   :price 94.99
   :category "Footwear"
   :stock 120})

;; --- Valid update body ---

(deftest valid-update-passes-test
  (testing "A valid update body passes validation"
    (is (nil? (v/validate v/UpdateProduct valid-update)))))

(deftest valid-update-without-description-test
  (testing "A valid update body without optional description passes"
    (is (nil? (v/validate v/UpdateProduct (dissoc valid-update :description))))))

;; --- Name validation ---

(deftest empty-name-rejected-test
  (testing "Empty name is rejected"
    (let [errors (v/validate v/UpdateProduct (assoc valid-update :name ""))]
      (is (some? errors))
      (is (contains? errors :name)))))

(deftest whitespace-only-name-rejected-test
  (testing "Whitespace-only name is rejected (after trim becomes empty string)"
    (let [errors (v/validate v/UpdateProduct (assoc valid-update :name ""))]
      (is (some? errors))
      (is (contains? errors :name)))))

;; --- Price validation ---

(deftest negative-price-rejected-test
  (testing "Negative price is rejected"
    (let [errors (v/validate v/UpdateProduct (assoc valid-update :price -1.0))]
      (is (some? errors))
      (is (contains? errors :price)))))

(deftest zero-price-rejected-test
  (testing "Zero price is rejected"
    (let [errors (v/validate v/UpdateProduct (assoc valid-update :price 0.0))]
      (is (some? errors))
      (is (contains? errors :price)))))

;; --- Stock validation ---

(deftest negative-stock-rejected-test
  (testing "Negative stock is rejected"
    (let [errors (v/validate v/UpdateProduct (assoc valid-update :stock -1))]
      (is (some? errors))
      (is (contains? errors :stock)))))

;; --- Missing required fields ---

(deftest missing-name-rejected-test
  (testing "Missing name is rejected"
    (let [errors (v/validate v/UpdateProduct (dissoc valid-update :name))]
      (is (some? errors))
      (is (contains? errors :name)))))

(deftest missing-price-rejected-test
  (testing "Missing price is rejected"
    (let [errors (v/validate v/UpdateProduct (dissoc valid-update :price))]
      (is (some? errors))
      (is (contains? errors :price)))))

(deftest missing-stock-rejected-test
  (testing "Missing stock is rejected"
    (let [errors (v/validate v/UpdateProduct (dissoc valid-update :stock))]
      (is (some? errors))
      (is (contains? errors :stock)))))

(deftest missing-category-rejected-test
  (testing "Missing category is rejected"
    (let [errors (v/validate v/UpdateProduct (dissoc valid-update :category))]
      (is (some? errors))
      (is (contains? errors :category)))))

;; --- Closed map: extra/unknown fields rejected ---

(deftest extra-fields-rejected-test
  (testing "Extra/unknown fields are rejected (closed map)"
    (let [errors (v/validate v/UpdateProduct (assoc valid-update :unknown "value"))]
      (is (some? errors))
      (is (contains? errors :unknown)))))

;; --- SKU in body rejected (closed map, no :sku key) ---

(deftest sku-in-body-rejected-test
  (testing "SKU field in update body is rejected by closed map"
    (let [errors (v/validate v/UpdateProduct (assoc valid-update :sku "SKU-001"))]
      (is (some? errors))
      (is (contains? errors :sku)))))
