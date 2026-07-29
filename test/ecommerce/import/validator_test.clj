(ns ecommerce.import.validator-test
  (:require [clojure.test :refer [deftest is testing]]
            [ecommerce.import.validator :as validator]))

(defn- make-row
  "Build a CSV row vector from a map of overrides.
   Default values produce a valid product row."
  [overrides]
  (let [defaults {:name "Test Product"
                  :sku "TP-001"
                  :description "A test product"
                  :category "Electronics"
                  :price "29.99"
                  :stock "10"
                  :weight_kg "1.5"}
        m (merge defaults overrides)]
    [(:name m) (:sku m) (:description m) (:category m)
     (:price m) (:stock m) (:weight_kg m)]))

;; --- Trap 1: Malformed price ---

(deftest malformed-price-free-test
  (testing "Price 'free' is rejected with field_name=price"
    (let [row (make-row {:price "free"})
          result (validator/validate-row row (atom #{}))]
      (is (= :invalid (:status result)))
      (is (contains? (:errors result) :price)))))

(deftest malformed-price-dollar-sign-test
  (testing "Price '$29.99' is rejected with field_name=price"
    (let [row (make-row {:price "$29.99"})
          result (validator/validate-row row (atom #{}))]
      (is (= :invalid (:status result)))
      (is (contains? (:errors result) :price)))))

(deftest malformed-price-alpha-test
  (testing "Price 'abc' is rejected with field_name=price"
    (let [row (make-row {:price "abc"})
          result (validator/validate-row row (atom #{}))]
      (is (= :invalid (:status result)))
      (is (contains? (:errors result) :price)))))

;; --- Trap 2: Negative stock ---

(deftest negative-stock-test
  (testing "Negative stock -5 is rejected with field_name=stock"
    (let [row (make-row {:stock "-5"})
          result (validator/validate-row row (atom #{}))]
      (is (= :invalid (:status result)))
      (is (contains? (:errors result) :stock)))))

;; --- Trap 3: Empty name ---

(deftest empty-name-test
  (testing "Empty name is rejected with field_name=name"
    (let [row (make-row {:name ""})
          result (validator/validate-row row (atom #{}))]
      (is (= :invalid (:status result)))
      (is (contains? (:errors result) :name)))))

(deftest whitespace-only-name-test
  (testing "Whitespace-only name is rejected with field_name=name"
    (let [row (make-row {:name "   "})
          result (validator/validate-row row (atom #{}))]
      (is (= :invalid (:status result)))
      (is (contains? (:errors result) :name)))))

;; --- Trap 4: Missing category (ACCEPT) ---

(deftest missing-category-accepted-test
  (testing "Empty category is accepted (optional in CSV context)"
    (let [row (make-row {:category ""})
          result (validator/validate-row row (atom #{}))]
      (is (= :valid (:status result)))
      (is (not (contains? (:product-map result) :category))))))

;; --- Trap 5: Missing weight_kg (ACCEPT) ---

(deftest missing-weight-accepted-test
  (testing "Empty weight_kg is accepted (always stripped by parser)"
    (let [row (make-row {:weight_kg ""})
          result (validator/validate-row row (atom #{}))]
      (is (= :valid (:status result)))
      (is (not (contains? (:product-map result) :weight_kg))))))

;; --- Trap 6: Empty rows (SKIP) ---

(deftest empty-row-skipped-test
  (testing "All-blank row is skipped silently"
    (let [row ["" "" "" "" "" "" ""]
          result (validator/validate-row row (atom #{}))]
      (is (= :blank (:status result)))
      (is (nil? (:product-map result)))
      (is (nil? (:errors result))))))

(deftest whitespace-only-row-skipped-test
  (testing "All-whitespace row is skipped silently"
    (let [row ["  " "  " "  " "  " "  " "  " "  "]
          result (validator/validate-row row (atom #{}))]
      (is (= :blank (:status result))))))

;; --- Trap 7: Duplicate SKU in-file ---

(deftest duplicate-sku-in-file-test
  (testing "Second occurrence of same SKU in file is rejected"
    (let [seen-skus (atom #{})
          row1 (make-row {:sku "DUP-001"})
          row2 (make-row {:sku "DUP-001" :name "Different Product"})
          result1 (validator/validate-row row1 seen-skus)
          result2 (validator/validate-row row2 seen-skus)]
      ;; First occurrence accepted
      (is (= :valid (:status result1)))
      (is (contains? @seen-skus "DUP-001"))
      ;; Second occurrence rejected as duplicate
      (is (= :duplicate (:status result2)))
      (is (contains? (:errors result2) :sku)))))

;; --- Trap 8: Duplicate SKU in catalog (UPSERT — validator returns :valid) ---

(deftest catalog-duplicate-passes-validation-test
  (testing "SKU that exists in catalog still passes validator (upsert handled by worker)"
    (let [row (make-row {:sku "CATALOG-001"})
          result (validator/validate-row row (atom #{}))]
      (is (= :valid (:status result))))))

;; --- Trap 9: XSS payload (REJECT) ---

(deftest xss-script-tag-rejected-test
  (testing "Script tag in name is rejected with 'Unsafe content detected'"
    (let [row (make-row {:name "<script>alert(1)</script>"})
          result (validator/validate-row row (atom #{}))]
      (is (= :xss (:status result)))
      (is (= "Unsafe content detected" (:name (:errors result)))))))

(deftest xss-event-handler-rejected-test
  (testing "Event handler attribute onerror= in name is rejected"
    (let [row (make-row {:name "Product onerror=alert(1)"})
          result (validator/validate-row row (atom #{}))]
      (is (= :xss (:status result)))
      (is (= "Unsafe content detected" (:name (:errors result)))))))

(deftest xss-javascript-uri-rejected-test
  (testing "javascript: URI in name is rejected"
    (let [row (make-row {:name "javascript:alert(1)"})
          result (validator/validate-row row (atom #{}))]
      (is (= :xss (:status result)))
      (is (= "Unsafe content detected" (:name (:errors result)))))))

(deftest xss-in-description-rejected-test
  (testing "XSS in description field is also detected"
    (let [row (make-row {:description "<script>steal()</script>"})
          result (validator/validate-row row (atom #{}))]
      (is (= :xss (:status result)))
      (is (some #(= "Unsafe content detected" (val %)) (:errors result))))))

;; --- XSS false positives that must NOT be rejected ---

(deftest xss-apostrophe-not-rejected-test
  (testing "Apostrophe in name is NOT rejected (O'Brien's Widget is valid)"
    (let [row (make-row {:name "O'Brien's Widget"})
          result (validator/validate-row row (atom #{}))]
      (is (= :valid (:status result))))))

(deftest xss-angle-brackets-not-rejected-test
  (testing "Generic angle brackets are NOT rejected (Widget <3 pack> is valid)"
    (let [row (make-row {:name "Widget <3 pack>"})
          result (validator/validate-row row (atom #{}))]
      (is (= :valid (:status result))))))

;; --- Trap 10: SQL injection (ACCEPT) ---

(deftest sql-injection-accepted-test
  (testing "SQL injection in name is accepted (parameterized queries handle it)"
    (let [row (make-row {:name "'; DROP TABLE products; --"})
          result (validator/validate-row row (atom #{}))]
      (is (= :valid (:status result)))
      (is (= "'; DROP TABLE products; --" (:name (:product-map result)))))))

;; --- Shared Malli validation contract ---

(deftest uses-shared-malli-schema-price-ceiling-test
  (testing "CSVProduct inherits price ceiling from shared Product schema"
    (let [row (make-row {:price "100000.00"})
          result (validator/validate-row row (atom #{}))]
      (is (= :invalid (:status result)))
      (is (contains? (:errors result) :price)))))

(deftest uses-shared-malli-schema-sku-max-length-test
  (testing "CSVProduct inherits SKU max length from shared Product schema"
    (let [long-sku (apply str (repeat 51 "A"))
          row (make-row {:sku long-sku})
          result (validator/validate-row row (atom #{}))]
      (is (= :invalid (:status result)))
      (is (contains? (:errors result) :sku)))))

;; --- Row count reconciliation ---

(deftest row-count-reconciliation-test
  (testing "Accepted + rejected + skipped accounts for every row"
    (let [seen-skus (atom #{})
          rows [;; valid
                (make-row {:sku "RC-001"})
                ;; blank — skipped
                ["" "" "" "" "" "" ""]
                ;; invalid (bad price)
                (make-row {:sku "RC-002" :price "free"})
                ;; valid
                (make-row {:sku "RC-003"})
                ;; xss — rejected
                (make-row {:sku "RC-004" :name "<script>x</script>"})]
          results (mapv #(validator/validate-row % seen-skus) rows)
          statuses (mapv :status results)
          accepted (count (filter #{:valid} statuses))
          rejected (count (filter #{:invalid :xss :duplicate} statuses))
          skipped (count (filter #{:blank} statuses))]
      (is (= 2 accepted))
      (is (= 2 rejected))
      (is (= 1 skipped))
      ;; Every row is accounted for
      (is (= (count rows) (+ accepted rejected skipped))))))
