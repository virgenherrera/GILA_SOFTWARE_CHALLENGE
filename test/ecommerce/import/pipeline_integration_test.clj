(ns ecommerce.import.pipeline-integration-test
  "Integration tests for the full CSV import pipeline.
   Exercises: upload -> validate -> persist with mocked DB operations."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.async :as async]
            [ecommerce.import.worker :as worker]
            [ecommerce.import.repository :as import-repo]
            [ecommerce.product.repository :as product-repo]))

(defn- run-import-job
  "Run an import job through the worker pipeline and return results.
   Mocks all DB operations, captures errors and persisted products.
   Options:
     :existing-skus — set of SKUs that simulate pre-existing catalog products"
  [csv-content & {:keys [existing-skus] :or {existing-skus #{}}}]
  (let [job-id (java.util.UUID/randomUUID)
        completed (promise)
        errors (atom [])
        products (atom {})
        mock-ds (reify javax.sql.DataSource
                  (getConnection [_]
                    (throw (Exception. "Mock datasource"))))]
    (with-redefs [import-repo/update-job-processing!
                  (fn [_ds _id] nil)

                  import-repo/record-import-error!
                  (fn [_ds error-map]
                    (swap! errors conj error-map))

                  import-repo/update-job-completed!
                  (fn [_ds _id status total accepted rejected skipped]
                    (deliver completed
                             {:status status
                              :total total
                              :accepted accepted
                              :rejected rejected
                              :skipped skipped}))

                  product-repo/insert-product!
                  (fn [_ds product-map]
                    (let [sku (:sku product-map)]
                      (if (or (contains? existing-skus sku)
                              (contains? @products sku))
                        (throw (ex-info (str "Duplicate SKU: " sku)
                                        {:status 409 :code "CONFLICT"}))
                        (do (swap! products assoc sku product-map)
                            product-map))))

                  product-repo/update-product!
                  (fn [_ds sku product-map]
                    (swap! products assoc sku (assoc product-map :sku sku))
                    (get @products sku))]
      (let [channel (async/chan 10)]
        (worker/start-worker mock-ds channel)
        (async/put! channel {:job-id job-id :csv-content csv-content})
        (async/close! channel)
        (let [result (deref completed 5000 :timeout)]
          (when (= :timeout result)
            (throw (ex-info "Worker timed out" {})))
          {:result result
           :errors @errors
           :products @products})))))

;; --- Pipeline: all valid rows ---

(deftest pipeline-all-valid-test
  (testing "All valid rows are accepted and persisted"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   "Widget A,WA-001,Desc A,Electronics,29.99,10,1.5\n"
                   "Widget B,WB-001,Desc B,Toys,9.99,100,0.5")
          {:keys [result errors products]} (run-import-job csv)]
      (is (= "Completed" (:status result)))
      (is (= 2 (:accepted result)))
      (is (= 0 (:rejected result)))
      (is (= 0 (:skipped result)))
      (is (= 2 (:total result)))
      (is (empty? errors))
      (is (= 2 (count products))))))

;; --- Pipeline: XSS rejection ---

(deftest pipeline-xss-rejection-test
  (testing "XSS row is rejected with XSS_ERROR code and correct message"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   "<script>alert(1)</script>,XSS-001,Desc,Electronics,29.99,10,1.5")
          {:keys [result errors]} (run-import-job csv)]
      (is (= "Failed" (:status result)))
      (is (= 0 (:accepted result)))
      (is (= 1 (:rejected result)))
      (is (= 1 (count errors)))
      (is (= "XSS_ERROR" (:error_code (first errors))))
      (is (= "name" (:field (first errors))))
      (is (= "Unsafe content detected" (:message (first errors)))))))

;; --- Pipeline: duplicate SKU in-file ---

(deftest pipeline-duplicate-sku-in-file-test
  (testing "First occurrence of duplicate SKU accepted, second rejected"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   "Product A,DUP-001,First,Electronics,29.99,10,1.5\n"
                   "Product B,DUP-001,Second,Electronics,19.99,5,0.5")
          {:keys [result errors products]} (run-import-job csv)]
      (is (= "Completed" (:status result)))
      (is (= 1 (:accepted result)))
      (is (= 1 (:rejected result)))
      ;; First occurrence persisted
      (is (= "Product A" (:name (get products "DUP-001"))))
      ;; Error recorded for duplicate
      (let [err (first errors)]
        (is (= "DUPLICATE_SKU" (:error_code err)))
        (is (= "sku" (:field err)))))))

;; --- Pipeline: duplicate SKU in catalog (upsert) ---

(deftest pipeline-catalog-upsert-test
  (testing "SKU matching existing catalog product triggers upsert, counts as accepted"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   "Updated Widget,EXIST-001,Updated desc,Electronics,39.99,20,2.0")
          {:keys [result errors products]}
          (run-import-job csv :existing-skus #{"EXIST-001"})]
      (is (= "Completed" (:status result)))
      (is (= 1 (:accepted result)))
      (is (= 0 (:rejected result)))
      (is (empty? errors))
      ;; Product was upserted
      (is (= "Updated Widget" (:name (get products "EXIST-001")))))))

;; --- Pipeline: empty rows skipped ---

(deftest pipeline-empty-rows-skipped-test
  (testing "Empty rows are skipped and not counted in accepted or rejected"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   "Valid,SK-001,Desc,Electronics,29.99,10,1.5\n"
                   ",,,,,,\n"
                   ",,,,,,\n"
                   "Also Valid,SK-002,Desc,Books,5.50,50,0.2")
          {:keys [result]} (run-import-job csv)]
      (is (= "Completed" (:status result)))
      (is (= 2 (:accepted result)))
      (is (= 0 (:rejected result)))
      (is (= 2 (:skipped result)))
      ;; total = accepted + rejected (skipped excluded)
      (is (= 2 (:total result))))))

;; --- Pipeline: missing category accepted ---

(deftest pipeline-missing-category-accepted-test
  (testing "Empty category is accepted in CSV import context"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   "No Category,NC-001,Desc,,29.99,10,1.5")
          {:keys [result errors products]} (run-import-job csv)]
      (is (= "Completed" (:status result)))
      (is (= 1 (:accepted result)))
      (is (= 0 (:rejected result)))
      (is (empty? errors))
      (is (some? (get products "NC-001"))))))

;; --- Pipeline: SQL injection accepted ---

(deftest pipeline-sql-injection-accepted-test
  (testing "SQL injection is stored as literal text, table intact"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   "'; DROP TABLE products; --,SQL-001,Desc,Electronics,29.99,10,1.5")
          {:keys [result products]} (run-import-job csv)]
      (is (= "Completed" (:status result)))
      (is (= 1 (:accepted result)))
      ;; Stored as literal text
      (is (= "'; DROP TABLE products; --"
             (:name (get products "SQL-001")))))))

;; --- Pipeline: mixed traps ---

(deftest pipeline-mixed-traps-test
  (testing "Mixed trap types produce correct counts and error recording"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   ;; Row 2: valid
                   "Valid Product,MIX-001,Good stuff,Electronics,29.99,10,0.5\n"
                   ;; Row 3: invalid (empty name, malformed price, negative stock)
                   ",MIX-002,No name,,free,-5,\n"
                   ;; Row 4: XSS
                   "<script>alert(1)</script>,MIX-003,XSS,Toys,19.99,5,0.3\n"
                   ;; Row 5: blank — skipped
                   ",,,,,,\n"
                   ;; Row 6: valid
                   "Another Valid,MIX-004,Also good,Books,5.50,50,0.2\n"
                   ;; Row 7: SQL injection — accepted
                   "'; DROP TABLE products; --,MIX-005,SQL is safe,Electronics,15.00,25,0.4\n"
                   ;; Row 8: duplicate SKU of MIX-001 — rejected
                   "Duplicate,MIX-001,Dup of first,Electronics,39.99,20,1.0")
          {:keys [result errors]} (run-import-job csv)]
      (is (= "Completed" (:status result)))
      (is (= 3 (:accepted result)))   ;; MIX-001 + MIX-004 + MIX-005
      (is (= 3 (:rejected result)))   ;; MIX-002 + MIX-003 + MIX-001 dup
      (is (= 1 (:skipped result)))    ;; blank row
      ;; total = accepted + rejected
      (is (= (:total result) (+ (:accepted result) (:rejected result))))
      ;; Errors recorded for each rejection
      (is (pos? (count errors)))
      ;; XSS error present
      (is (some #(= "XSS_ERROR" (:error_code %)) errors))
      ;; Duplicate error present
      (is (some #(= "DUPLICATE_SKU" (:error_code %)) errors))
      ;; Validation error present
      (is (some #(= "VALIDATION_ERROR" (:error_code %)) errors)))))

;; --- Pipeline: row count reconciliation ---

(deftest pipeline-row-count-reconciliation-test
  (testing "total = accepted + rejected; skipped tracked separately"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   "Good,RC-001,Desc,Electronics,29.99,10,1.5\n"
                   ",,,,,,\n"
                   ",RC-002,,Electronics,free,-5,\n"
                   "Also Good,RC-003,Desc,Books,5.50,50,0.2\n"
                   ",,,,,,")
          {:keys [result]} (run-import-job csv)]
      ;; total = accepted + rejected
      (is (= (:total result) (+ (:accepted result) (:rejected result))))
      (is (= 2 (:accepted result)))
      (is (= 1 (:rejected result)))
      (is (= 2 (:skipped result))))))

;; --- Pipeline: malformed prices rejected ---

(deftest pipeline-malformed-prices-test
  (testing "All malformed price formats are rejected"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   "Free Widget,MP-001,Desc,Electronics,free,10,0.5\n"
                   "Dollar Widget,MP-002,Desc,Electronics,$29.99,10,0.5\n"
                   "Alpha Widget,MP-003,Desc,Electronics,abc,10,0.5")
          {:keys [result errors]} (run-import-job csv)]
      (is (= "Failed" (:status result)))
      (is (= 0 (:accepted result)))
      (is (= 3 (:rejected result)))
      ;; All errors should be on price field
      (is (every? #(= "price" (:field %)) errors)))))

;; --- Pipeline: negative stock rejected ---

(deftest pipeline-negative-stock-test
  (testing "Negative stock values are rejected"
    (let [csv (str "name,sku,description,category,price,stock,weight_kg\n"
                   "Widget,NS-001,Desc,Electronics,29.99,-5,0.5")
          {:keys [result errors]} (run-import-job csv)]
      (is (= "Failed" (:status result)))
      (is (= 0 (:accepted result)))
      (is (= 1 (:rejected result)))
      (is (= "stock" (:field (first errors)))))))
