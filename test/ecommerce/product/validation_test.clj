(ns ecommerce.product.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [ecommerce.validation :as v]))

(def ^:private valid-product
  {:sku "SKU-001"
   :name "Test Product"
   :description "A test product"
   :price 29.99
   :category "Electronics"
   :stock 10})

(deftest valid-product-passes-test
  (testing "A valid product passes validation"
    (is (nil? (v/validate v/Product valid-product)))))

(deftest valid-product-without-description-test
  (testing "A valid product without optional description passes"
    (is (nil? (v/validate v/Product (dissoc valid-product :description))))))

;; --- Name validation ---

(deftest empty-name-rejected-test
  (testing "Empty name is rejected"
    (let [errors (v/validate v/Product (assoc valid-product :name ""))]
      (is (some? errors))
      (is (contains? errors :name)))))

(deftest whitespace-only-name-rejected-test
  (testing "Whitespace-only name is rejected (after trim in handler, schema sees empty string)"
    ;; The handler trims whitespace before validation.
    ;; After trim, a whitespace-only name becomes "", which fails min 1.
    (let [errors (v/validate v/Product (assoc valid-product :name ""))]
      (is (some? errors))
      (is (contains? errors :name)))))

(deftest name-max-256-chars-test
  (testing "Name at exactly 256 chars passes"
    (let [name-256 (apply str (repeat 256 "a"))]
      (is (nil? (v/validate v/Product (assoc valid-product :name name-256))))))
  (testing "Name at 257 chars is rejected"
    (let [name-257 (apply str (repeat 257 "a"))
          errors   (v/validate v/Product (assoc valid-product :name name-257))]
      (is (some? errors))
      (is (contains? errors :name)))))

(deftest missing-name-rejected-test
  (testing "Missing name is rejected"
    (let [errors (v/validate v/Product (dissoc valid-product :name))]
      (is (some? errors))
      (is (contains? errors :name)))))

;; --- SKU validation ---

(deftest empty-sku-rejected-test
  (testing "Empty SKU is rejected"
    (let [errors (v/validate v/Product (assoc valid-product :sku ""))]
      (is (some? errors))
      (is (contains? errors :sku)))))

(deftest missing-sku-rejected-test
  (testing "Missing SKU is rejected"
    (let [errors (v/validate v/Product (dissoc valid-product :sku))]
      (is (some? errors))
      (is (contains? errors :sku)))))

(deftest sku-max-50-chars-test
  (testing "SKU at exactly 50 chars passes"
    (let [sku-50 (apply str (repeat 50 "X"))]
      (is (nil? (v/validate v/Product (assoc valid-product :sku sku-50))))))
  (testing "SKU at 51 chars is rejected"
    (let [sku-51  (apply str (repeat 51 "X"))
          errors  (v/validate v/Product (assoc valid-product :sku sku-51))]
      (is (some? errors))
      (is (contains? errors :sku)))))

;; --- Price validation ---

(deftest negative-price-rejected-test
  (testing "Negative price is rejected"
    (let [errors (v/validate v/Product (assoc valid-product :price -1.0))]
      (is (some? errors))
      (is (contains? errors :price)))))

(deftest zero-price-rejected-test
  (testing "Zero price is rejected"
    (let [errors (v/validate v/Product (assoc valid-product :price 0.0))]
      (is (some? errors))
      (is (contains? errors :price)))))

(deftest price-exceeds-max-rejected-test
  (testing "Price > 99999.99 is rejected"
    (let [errors (v/validate v/Product (assoc valid-product :price 100000.0))]
      (is (some? errors))
      (is (contains? errors :price)))))

(deftest price-at-max-boundary-test
  (testing "Price at exactly 99999.99 passes"
    (is (nil? (v/validate v/Product (assoc valid-product :price 99999.99))))))

(deftest price-at-min-boundary-test
  (testing "Price at 0.01 passes"
    (is (nil? (v/validate v/Product (assoc valid-product :price 0.01))))))

(deftest missing-price-rejected-test
  (testing "Missing price is rejected"
    (let [errors (v/validate v/Product (dissoc valid-product :price))]
      (is (some? errors))
      (is (contains? errors :price)))))

;; --- Stock validation ---

(deftest negative-stock-rejected-test
  (testing "Negative stock is rejected"
    (let [errors (v/validate v/Product (assoc valid-product :stock -1))]
      (is (some? errors))
      (is (contains? errors :stock)))))

(deftest zero-stock-passes-test
  (testing "Zero stock passes (>= 0)"
    (is (nil? (v/validate v/Product (assoc valid-product :stock 0))))))

(deftest missing-stock-rejected-test
  (testing "Missing stock is rejected"
    (let [errors (v/validate v/Product (dissoc valid-product :stock))]
      (is (some? errors))
      (is (contains? errors :stock)))))

;; --- Category validation ---

(deftest missing-category-rejected-test
  (testing "Missing category is rejected"
    (let [errors (v/validate v/Product (dissoc valid-product :category))]
      (is (some? errors))
      (is (contains? errors :category)))))

(deftest empty-category-rejected-test
  (testing "Empty category is rejected"
    (let [errors (v/validate v/Product (assoc valid-product :category ""))]
      (is (some? errors))
      (is (contains? errors :category)))))

(deftest category-max-100-chars-test
  (testing "Category at exactly 100 chars passes"
    (let [cat-100 (apply str (repeat 100 "c"))]
      (is (nil? (v/validate v/Product (assoc valid-product :category cat-100))))))
  (testing "Category at 101 chars is rejected"
    (let [cat-101 (apply str (repeat 101 "c"))
          errors  (v/validate v/Product (assoc valid-product :category cat-101))]
      (is (some? errors))
      (is (contains? errors :category)))))

;; --- Description validation ---

(deftest description-max-4096-chars-test
  (testing "Description at exactly 4096 chars passes"
    (let [desc-4096 (apply str (repeat 4096 "d"))]
      (is (nil? (v/validate v/Product (assoc valid-product :description desc-4096))))))
  (testing "Description at 4097 chars is rejected"
    (let [desc-4097 (apply str (repeat 4097 "d"))
          errors    (v/validate v/Product (assoc valid-product :description desc-4097))]
      (is (some? errors))
      (is (contains? errors :description)))))

;; --- Closed map: extra/unknown fields rejected ---

(deftest extra-fields-rejected-test
  (testing "Extra/unknown fields are rejected (closed map)"
    (let [errors (v/validate v/Product (assoc valid-product :unknown "value"))]
      (is (some? errors))
      (is (contains? errors :unknown)))))

;; --- Multi-error collection ---

(deftest multiple-errors-collected-test
  (testing "Multiple validation errors are collected in one call"
    (let [invalid {:sku ""
                   :name ""
                   :price -1.0
                   :category ""
                   :stock -1}
          errors (v/validate v/Product invalid)]
      (is (some? errors))
      (is (contains? errors :sku))
      (is (contains? errors :name))
      (is (contains? errors :price))
      (is (contains? errors :category))
      (is (contains? errors :stock)))))

;; --- All required fields missing ---

(deftest all-required-fields-missing-test
  (testing "Missing all required fields produces errors for each"
    (let [errors (v/validate v/Product {})]
      (is (some? errors))
      (is (contains? errors :sku))
      (is (contains? errors :name))
      (is (contains? errors :price))
      (is (contains? errors :category))
      (is (contains? errors :stock)))))
