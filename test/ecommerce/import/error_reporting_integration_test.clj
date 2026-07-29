(ns ecommerce.import.error-reporting-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [ring.mock.request :as mock]
            [ecommerce.router :as router]
            [ecommerce.import.repository :as import-repo]
            [ecommerce.import.error-repository :as error-repo]))

(defn- mock-datasource
  "Create a mock datasource (not connected to a real DB)."
  []
  (reify javax.sql.DataSource
    (getConnection [_]
      (throw (Exception. "Mock datasource - no real connection")))))

(defn- parse-body
  "Parse JSON response body to a Clojure map with keyword keys."
  [response]
  (json/read-str (:body response) :key-fn keyword))

(defn- get-request
  "Create a GET request to the given path and run it through the handler."
  [handler path]
  (handler (mock/request :get path)))

;; --- Sample data ---

(def ^:private job-id (java.util.UUID/randomUUID))

(def ^:private sample-job
  {:id job-id
   :filename "products.csv"
   :status "CompletedWithErrors"
   :total_rows 10
   :accepted_rows 7
   :rejected_rows 3
   :skipped_rows 0})

(def ^:private sample-errors
  [{:row_number 2  :field "price"    :value "abc"                            :message "Invalid price format"}
   {:row_number 5  :field "sku"      :value ""                               :message "SKU is required"}
   {:row_number 6  :field nil        :value ",,,,,,"                         :message "Empty row"}])

;; ============================================================
;; 1. Summary returns counts (GET /api/imports/:id)
;; ============================================================

(deftest summary-returns-counts-test
  (testing "GET /api/imports/:id returns row counts in response"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))]
        (let [response (get-request handler (str "/api/imports/" job-id))
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= 10 (:total_rows body)))
          (is (= 7 (:accepted_rows body)))
          (is (= 3 (:rejected_rows body)))
          (is (= 0 (:skipped_rows body))))))))

;; ============================================================
;; 2. Status distinction (Completed vs CompletedWithErrors)
;; ============================================================

(deftest status-distinction-test
  (testing "Job with errors has CompletedWithErrors status"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))]
        (let [response (get-request handler (str "/api/imports/" job-id))
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= "CompletedWithErrors" (:status body)))))))

  (testing "Job without errors has Completed status"
    (let [handler (router/create-router (mock-datasource))
          clean-job (assoc sample-job :status "Completed" :rejected_rows 0 :accepted_rows 10)]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) clean-job))]
        (let [response (get-request handler (str "/api/imports/" job-id))
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= "Completed" (:status body))))))))

;; ============================================================
;; 3. Paginated errors (items + paging envelope)
;; ============================================================

(deftest paginated-errors-test
  (testing "GET /api/imports/:id/errors returns items and paging envelope"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page] sample-errors)
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 3)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors"))
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (vector? (:items body)))
          (is (= 3 (count (:items body))))
          (is (map? (:paging body)))
          (is (= 1 (get-in body [:paging :page])))
          (is (= 20 (get-in body [:paging :perPage])))
          (is (= 3 (get-in body [:paging :total])))
          (is (nil? (get-in body [:paging :prev])))
          (is (nil? (get-in body [:paging :next]))))))))

;; ============================================================
;; 4. Error item has correct shape
;; ============================================================

(deftest error-item-shape-test
  (testing "Each error item has row_number, field_name, error_reason, raw_row_data, product_sku"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page] sample-errors)
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 3)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors"))
              body (parse-body response)
              item (first (:items body))]
          (is (= 200 (:status response)))
          ;; Required keys present
          (is (contains? item :row_number))
          (is (contains? item :raw_row_data))
          (is (contains? item :field_name))
          (is (contains? item :error_reason))
          (is (contains? item :product_sku))
          ;; Values mapped correctly from DB columns
          (is (= 2 (:row_number item)))
          (is (= "abc" (:raw_row_data item)))
          (is (= "price" (:field_name item)))
          (is (= "Invalid price format" (:error_reason item)))
          (is (nil? (:product_sku item)))
          ;; field_name can be nil (third error)
          (let [nil-field-item (nth (:items body) 2)]
            (is (nil? (:field_name nil-field-item)))))))))

;; ============================================================
;; 5. raw_row_data sanitized (script tags encoded)
;; ============================================================

(deftest raw-row-data-sanitized-test
  (testing "Script tags in raw_row_data are HTML-entity encoded"
    (let [handler (router/create-router (mock-datasource))
          xss-error [{:row_number 3
                      :field "name"
                      :value "<script>alert('xss')</script>"
                      :message "Validation failed"}]]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page] xss-error)
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 1)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors"))
              body (parse-body response)
              item (first (:items body))]
          (is (= 200 (:status response)))
          ;; Angle brackets must be encoded
          (is (not (str/includes? (:raw_row_data item) "<script>")))
          (is (str/includes? (:raw_row_data item) "&lt;script&gt;"))
          (is (str/includes? (:raw_row_data item) "&lt;/script&gt;"))))))

  (testing "Ampersands in raw_row_data are encoded before angle brackets"
    (let [handler (router/create-router (mock-datasource))
          amp-error [{:row_number 1
                      :field "name"
                      :value "A & B <C>"
                      :message "Validation failed"}]]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page] amp-error)
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 1)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors"))
              body (parse-body response)
              item (first (:items body))]
          (is (= "A &amp; B &lt;C&gt;" (:raw_row_data item)))))))

  (testing "Nil raw_row_data passes through as nil"
    (let [handler (router/create-router (mock-datasource))
          nil-value-error [{:row_number 1
                            :field "name"
                            :value nil
                            :message "Missing field"}]]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page] nil-value-error)
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 1)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors"))
              body (parse-body response)
              item (first (:items body))]
          (is (nil? (:raw_row_data item))))))))

;; ============================================================
;; 6. Non-existent job returns 404 (both endpoints)
;; ============================================================

(deftest non-existent-job-status-404-test
  (testing "GET /api/imports/:id for non-existent job returns 404"
    (let [handler (router/create-router (mock-datasource))
          fake-id (java.util.UUID/randomUUID)]
      (with-redefs [import-repo/find-job-by-id (fn [_ds _id] nil)]
        (let [response (get-request handler (str "/api/imports/" fake-id))
              body (parse-body response)]
          (is (= 404 (:status response)))
          (is (= "NOT_FOUND" (get-in body [:error :code]))))))))

(deftest non-existent-job-errors-404-test
  (testing "GET /api/imports/:id/errors for non-existent job returns 404"
    (let [handler (router/create-router (mock-datasource))
          fake-id (java.util.UUID/randomUUID)]
      (with-redefs [import-repo/find-job-by-id (fn [_ds _id] nil)]
        (let [response (get-request handler (str "/api/imports/" fake-id "/errors"))
              body (parse-body response)]
          (is (= 404 (:status response)))
          (is (= "NOT_FOUND" (get-in body [:error :code]))))))))

(deftest invalid-uuid-errors-400-test
  (testing "GET /api/imports/:id/errors with invalid UUID returns 400"
    (let [handler (router/create-router (mock-datasource))
          response (get-request handler "/api/imports/not-a-uuid/errors")
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code]))))))

;; ============================================================
;; 7. Paging preserves params (prev/next URLs)
;; ============================================================

(deftest paging-preserves-params-test
  (testing "Paging URLs include page and perPage params"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page]
                      (take 5 (repeat (first sample-errors))))
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 50)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors?page=2&perPage=5"))
              body (parse-body response)
              prev-url (get-in body [:paging :prev])
              next-url (get-in body [:paging :next])]
          (is (= 200 (:status response)))
          ;; Prev URL
          (is (some? prev-url))
          (is (str/includes? prev-url "page=1"))
          (is (str/includes? prev-url "perPage=5"))
          (is (str/includes? prev-url (str "/api/imports/" job-id "/errors")))
          ;; Next URL
          (is (some? next-url))
          (is (str/includes? next-url "page=3"))
          (is (str/includes? next-url "perPage=5")))))))

(deftest first-page-has-no-prev-test
  (testing "First page has prev=null"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page] sample-errors)
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 50)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors?page=1&perPage=20"))
              body (parse-body response)]
          (is (nil? (get-in body [:paging :prev])))
          (is (some? (get-in body [:paging :next]))))))))

(deftest last-page-has-no-next-test
  (testing "Last page has next=null"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page] [(first sample-errors)])
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 3)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors?page=1&perPage=20"))
              body (parse-body response)]
          (is (nil? (get-in body [:paging :next])))
          (is (nil? (get-in body [:paging :prev]))))))))

;; ============================================================
;; 8. Page beyond last returns 200 with empty items
;; ============================================================

(deftest page-beyond-last-returns-empty-items-test
  (testing "Page beyond last returns 200 with empty items and correct total"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page] [])
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 3)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors?page=100&perPage=20"))
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= [] (:items body)))
          (is (= 100 (get-in body [:paging :page])))
          (is (= 3 (get-in body [:paging :total])))
          (is (some? (get-in body [:paging :prev])))
          (is (nil? (get-in body [:paging :next]))))))))

;; ============================================================
;; 9. No internal details leaked
;; ============================================================

(deftest no-internal-details-leaked-test
  (testing "Error responses do not leak internal details"
    (let [handler (router/create-router (mock-datasource))
          fake-id (java.util.UUID/randomUUID)]
      (with-redefs [import-repo/find-job-by-id (fn [_ds _id] nil)]
        (let [response (get-request handler (str "/api/imports/" fake-id "/errors"))
              body-str (:body response)]
          (is (= 404 (:status response)))
          (is (not (re-find #"Exception" body-str)))
          (is (not (re-find #"clojure\." body-str)))
          (is (not (re-find #"java\." body-str)))
          (is (not (re-find #"SELECT|INSERT|DELETE" body-str)))
          (is (not (re-find #"datasource" body-str)))))))

  (testing "Success responses do not contain error_code (internal field)"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page] sample-errors)
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 3)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors"))
              body-str (:body response)]
          (is (= 200 (:status response)))
          ;; error_code is an internal DB field, should not appear in API response
          (is (not (str/includes? body-str "error_code"))))))))

;; ============================================================
;; Additional edge cases
;; ============================================================

(deftest invalid-pagination-params-400-test
  (testing "page=0 returns 400"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors?page=0"))
              body (parse-body response)]
          (is (= 400 (:status response)))
          (is (= "VALIDATION_ERROR" (get-in body [:error :code])))))))

  (testing "perPage=200 (over max) returns 400"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors?perPage=200"))
              body (parse-body response)]
          (is (= 400 (:status response)))
          (is (= "VALIDATION_ERROR" (get-in body [:error :code])))))))

  (testing "page=abc returns 400"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) sample-job))]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors?page=abc"))
              body (parse-body response)]
          (is (= 400 (:status response)))
          (is (= "VALIDATION_ERROR" (get-in body [:error :code]))))))))

(deftest zero-errors-returns-empty-items-test
  (testing "Job with zero errors returns 200 with empty items"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id) (assoc sample-job :status "Completed" :rejected_rows 0)))
                    error-repo/list-errors-by-job
                    (fn [_ds _jid _page _per-page] [])
                    error-repo/count-errors-by-job
                    (fn [_ds _jid] 0)]
        (let [response (get-request handler (str "/api/imports/" job-id "/errors"))
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= [] (:items body)))
          (is (= 0 (get-in body [:paging :total])))
          (is (nil? (get-in body [:paging :prev])))
          (is (nil? (get-in body [:paging :next]))))))))
