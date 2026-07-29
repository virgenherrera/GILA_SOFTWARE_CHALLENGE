(ns ecommerce.import.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [ecommerce.import.parser :as parser]))

;; --- Header validation ---

(deftest validate-headers-valid-test
  (testing "Valid headers return nil (no error)"
    (is (nil? (parser/validate-headers
               ["name" "sku" "description" "category" "price" "stock" "weight_kg"])))))

(deftest validate-headers-with-whitespace-test
  (testing "Headers with extra whitespace are trimmed and accepted"
    (is (nil? (parser/validate-headers
               [" name " " sku " " description " " category " " price " " stock " " weight_kg "])))))

(deftest validate-headers-wrong-columns-test
  (testing "Wrong column names return an error string"
    (let [result (parser/validate-headers ["foo" "bar" "baz"])]
      (is (string? result))
      (is (re-find #"Invalid CSV headers" result)))))

(deftest validate-headers-missing-column-test
  (testing "Missing a column returns an error"
    (is (some? (parser/validate-headers
                ["name" "sku" "description" "category" "price" "stock"])))))

(deftest validate-headers-extra-column-test
  (testing "Extra column returns an error"
    (is (some? (parser/validate-headers
                ["name" "sku" "description" "category" "price" "stock" "weight_kg" "extra"])))))

(deftest validate-headers-wrong-order-test
  (testing "Correct columns in wrong order returns an error"
    (is (some? (parser/validate-headers
                ["sku" "name" "description" "category" "price" "stock" "weight_kg"])))))

;; --- parse-csv ---

(deftest parse-csv-valid-test
  (testing "Valid CSV with header and data rows"
    (let [csv-str "name,sku,description,category,price,stock,weight_kg\nWidget,W-001,A widget,Electronics,29.99,10,1.5"
          result (parser/parse-csv csv-str)]
      (is (nil? (:error result)))
      (is (= ["name" "sku" "description" "category" "price" "stock" "weight_kg"]
             (:headers result)))
      (is (= 1 (count (:rows result))))
      (is (= ["Widget" "W-001" "A widget" "Electronics" "29.99" "10" "1.5"]
             (first (:rows result)))))))

(deftest parse-csv-empty-file-test
  (testing "Empty CSV returns an error"
    (let [result (parser/parse-csv "")]
      (is (= "CSV file is empty" (:error result))))))

(deftest parse-csv-header-only-test
  (testing "CSV with only a header row returns no data rows"
    (let [csv-str "name,sku,description,category,price,stock,weight_kg"
          result (parser/parse-csv csv-str)]
      (is (nil? (:error result)))
      (is (empty? (:rows result))))))

(deftest parse-csv-wrong-headers-test
  (testing "CSV with wrong headers returns an error"
    (let [csv-str "foo,bar,baz\n1,2,3"
          result (parser/parse-csv csv-str)]
      (is (string? (:error result)))
      (is (re-find #"Invalid CSV headers" (:error result))))))

(deftest parse-csv-multiple-rows-test
  (testing "CSV with multiple data rows"
    (let [csv-str (str "name,sku,description,category,price,stock,weight_kg\n"
                       "Widget A,W-001,Desc A,Electronics,29.99,10,1.5\n"
                       "Widget B,W-002,Desc B,Toys,9.99,100,0.5\n"
                       "Widget C,W-003,Desc C,Books,5.50,50,0.2")
          result (parser/parse-csv csv-str)]
      (is (nil? (:error result)))
      (is (= 3 (count (:rows result)))))))

;; --- row->product-map ---

(deftest row-to-product-map-valid-test
  (testing "Valid row converts to correct product map"
    (let [row ["Widget" "W-001" "A widget" "Electronics" "29.99" "10" "1.5"]
          product (parser/row->product-map row)]
      (is (= "Widget" (:name product)))
      (is (= "W-001" (:sku product)))
      (is (= "A widget" (:description product)))
      (is (= "Electronics" (:category product)))
      (is (= 29.99 (:price product)))
      (is (= 10 (:stock product)))
      ;; weight_kg should NOT be in the product map
      (is (not (contains? product :weight_kg))))))

(deftest row-to-product-map-blank-description-test
  (testing "Blank description is omitted from product map (optional field)"
    (let [row ["Widget" "W-001" "" "Electronics" "29.99" "10" "1.5"]
          product (parser/row->product-map row)]
      (is (not (contains? product :description))))))

(deftest row-to-product-map-trims-whitespace-test
  (testing "Whitespace in fields is trimmed"
    (let [row ["  Widget  " "  W-001  " "  A widget  " "  Electronics  " "  29.99  " "  10  " "1.5"]
          product (parser/row->product-map row)]
      (is (= "Widget" (:name product)))
      (is (= "W-001" (:sku product)))
      (is (= "Electronics" (:category product))))))

(deftest row-to-product-map-bad-price-test
  (testing "Non-numeric price results in nil"
    (let [row ["Widget" "W-001" "Desc" "Electronics" "not-a-number" "10" "1.5"]
          product (parser/row->product-map row)]
      (is (nil? (:price product))))))

(deftest row-to-product-map-bad-stock-test
  (testing "Non-numeric stock results in nil"
    (let [row ["Widget" "W-001" "Desc" "Electronics" "29.99" "abc" "1.5"]
          product (parser/row->product-map row)]
      (is (nil? (:stock product))))))

(deftest row-to-product-map-decimal-stock-test
  (testing "Decimal stock (non-integer) results in nil"
    (let [row ["Widget" "W-001" "Desc" "Electronics" "29.99" "10.5" "1.5"]
          product (parser/row->product-map row)]
      (is (nil? (:stock product))))))

;; --- classify-row ---

(deftest classify-row-valid-test
  (testing "Valid data row is classified as :valid"
    (let [row ["Widget" "W-001" "Desc" "Electronics" "29.99" "10" "1.5"]
          result (parser/classify-row row)]
      (is (= :valid (:classification result)))
      (is (map? (:product-map result)))
      (is (nil? (:errors result))))))

(deftest classify-row-blank-test
  (testing "All-blank row is classified as :blank"
    (let [row ["" "" "" "" "" "" ""]
          result (parser/classify-row row)]
      (is (= :blank (:classification result))))))

(deftest classify-row-whitespace-only-test
  (testing "All-whitespace row is classified as :blank"
    (let [row ["  " "  " "  " "  " "  " "  " "  "]
          result (parser/classify-row row)]
      (is (= :blank (:classification result))))))

(deftest classify-row-invalid-test
  (testing "Row with validation errors is classified as :invalid"
    (let [row ["" "" "" "" "-1.0" "-5" "1.0"]
          result (parser/classify-row row)]
      (is (= :invalid (:classification result)))
      (is (map? (:errors result))))))

(deftest classify-row-missing-required-fields-test
  (testing "Row missing required fields is classified as :invalid"
    (let [row ["Widget" "" "Desc" "" "29.99" "10" "1.5"]
          result (parser/classify-row row)]
      (is (= :invalid (:classification result)))
      (is (contains? (:errors result) :sku)))))

;; --- validate-row ---

(deftest validate-row-valid-product-test
  (testing "Valid product map passes validation"
    (let [product {:sku "W-001" :name "Widget" :description "Desc"
                   :price 29.99 :category "Electronics" :stock 10}]
      (is (nil? (parser/validate-row product))))))

(deftest validate-row-negative-price-test
  (testing "Negative price fails validation"
    (let [product {:sku "W-001" :name "Widget" :description "Desc"
                   :price -1.0 :category "Electronics" :stock 10}
          errors (parser/validate-row product)]
      (is (some? errors))
      (is (contains? errors :price)))))

(deftest validate-row-nil-price-test
  (testing "Nil price (coercion failure) fails validation"
    (let [product {:sku "W-001" :name "Widget" :description "Desc"
                   :price nil :category "Electronics" :stock 10}
          errors (parser/validate-row product)]
      (is (some? errors))
      (is (contains? errors :price)))))
