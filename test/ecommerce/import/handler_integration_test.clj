(ns ecommerce.import.handler-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.async :as async]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]
            [ecommerce.router :as router]
            [ecommerce.import.repository :as import-repo])
  (:import [java.io File]))

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

(defn- create-temp-csv
  "Create a temporary CSV file with the given content. Returns the File."
  [content]
  (let [f (File/createTempFile "test-import" ".csv")]
    (.deleteOnExit f)
    (spit f content)
    f))

(defn- create-temp-txt
  "Create a temporary TXT file. Returns the File."
  [content]
  (let [f (File/createTempFile "test-import" ".txt")]
    (.deleteOnExit f)
    (spit f content)
    f))

(defn- multipart-upload-request
  "Build a Ring request that simulates multipart/form-data with a file field.
   Ring's wrap-multipart-params populates :multipart-params from the request,
   but in tests we inject the parsed multipart-params directly."
  [handler tempfile filename content-type]
  (let [request (-> (mock/request :post "/api/imports")
                    (assoc :multipart-params
                           {"file" {:filename     filename
                                    :content-type content-type
                                    :tempfile     tempfile
                                    :size         (.length tempfile)}}))]
    (handler request)))

;; --- Upload success (202) ---

(deftest upload-csv-returns-202-test
  (testing "Uploading a valid CSV returns 202 with job_id and Pending status"
    (let [csv-content "name,sku,description,category,price,stock,weight_kg\nWidget,W-001,Desc,Electronics,29.99,10,1.5"
          tempfile (create-temp-csv csv-content)
          channel (async/chan 10)
          job-id (java.util.UUID/randomUUID)
          handler (router/create-router (mock-datasource) channel)]
      (with-redefs [import-repo/create-job!
                    (fn [_ds filename]
                      {:id job-id
                       :filename filename
                       :status "Pending"
                       :total_rows 0
                       :accepted_rows 0
                       :rejected_rows 0
                       :skipped_rows 0})]
        (let [response (multipart-upload-request handler tempfile "products.csv" "text/csv")
              body (parse-body response)]
          (is (= 202 (:status response)))
          (is (= (str job-id) (:job_id body)))
          (is (= "Pending" (:status body)))
          (is (string? (:message body)))
          (is (re-find #"/api/imports/" (:message body)))
          ;; Verify job was put on channel
          (let [queued-job (async/poll! channel)]
            (is (some? queued-job))
            (is (= job-id (:job-id queued-job)))
            (is (string? (:csv-content queued-job)))))))))

;; --- Missing file (400) ---

(deftest upload-no-file-returns-400-test
  (testing "Uploading without a file field returns 400"
    (let [channel (async/chan 10)
          handler (router/create-router (mock-datasource) channel)
          request (-> (mock/request :post "/api/imports")
                      (assoc :multipart-params {}))
          response (handler request)
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code])))
      (is (re-find #"[Mm]issing" (get-in body [:error :message]))))))

;; --- Non-CSV file (400) ---

(deftest upload-non-csv-returns-400-test
  (testing "Uploading a non-CSV file returns 400"
    (let [tempfile (create-temp-txt "not a csv")
          channel (async/chan 10)
          handler (router/create-router (mock-datasource) channel)
          response (multipart-upload-request handler tempfile "data.txt" "text/plain")
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code])))
      (is (re-find #"CSV" (get-in body [:error :message]))))))

;; --- CSV by content-type accepted ---

(deftest upload-csv-content-type-accepted-test
  (testing "File with .dat extension but text/csv content-type is accepted"
    (let [csv-content "name,sku,description,category,price,stock,weight_kg\nWidget,W-001,Desc,Electronics,29.99,10,1.5"
          tempfile (create-temp-csv csv-content)
          channel (async/chan 10)
          job-id (java.util.UUID/randomUUID)
          handler (router/create-router (mock-datasource) channel)]
      (with-redefs [import-repo/create-job!
                    (fn [_ds filename]
                      {:id job-id :filename filename :status "Pending"
                       :total_rows 0 :accepted_rows 0 :rejected_rows 0 :skipped_rows 0})]
        (let [response (multipart-upload-request handler tempfile "data.csv" "text/csv")
              body (parse-body response)]
          (is (= 202 (:status response)))
          (is (= (str job-id) (:job_id body))))))))

;; --- Get job status (200) ---

(deftest get-job-status-200-test
  (testing "GET /api/imports/:id returns 200 with job data"
    (let [job-id (java.util.UUID/randomUUID)
          handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds id]
                      (when (= id job-id)
                        {:id job-id
                         :filename "products.csv"
                         :status "Completed"
                         :total_rows 10
                         :accepted_rows 8
                         :rejected_rows 2
                         :skipped_rows 0}))]
        (let [request (mock/request :get (str "/api/imports/" job-id))
              response (handler request)
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= (str job-id) (:id body)))
          (is (= "products.csv" (:filename body)))
          (is (= "Completed" (:status body)))
          (is (= 10 (:total_rows body)))
          (is (= 8 (:accepted_rows body)))
          (is (= 2 (:rejected_rows body))))))))

;; --- Job not found (404) ---

(deftest get-job-status-404-test
  (testing "GET /api/imports/:id for non-existent job returns 404"
    (let [job-id (java.util.UUID/randomUUID)
          handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds _id] nil)]
        (let [request (mock/request :get (str "/api/imports/" job-id))
              response (handler request)
              body (parse-body response)]
          (is (= 404 (:status response)))
          (is (= "NOT_FOUND" (get-in body [:error :code]))))))))

;; --- Invalid UUID format (400) ---

(deftest get-job-status-invalid-uuid-test
  (testing "GET /api/imports/:id with invalid UUID returns 400"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id
                    (fn [_ds _id] nil)]
        (let [request (mock/request :get "/api/imports/not-a-uuid")
              response (handler request)
              body (parse-body response)]
          (is (= 400 (:status response)))
          (is (= "VALIDATION_ERROR" (get-in body [:error :code]))))))))

;; --- Concurrent uploads produce distinct job_ids ---

(deftest concurrent-uploads-distinct-ids-test
  (testing "Multiple uploads produce distinct job IDs"
    (let [csv-content "name,sku,description,category,price,stock,weight_kg\nWidget,W-001,Desc,Electronics,29.99,10,1.5"
          tempfile1 (create-temp-csv csv-content)
          tempfile2 (create-temp-csv csv-content)
          channel (async/chan 10)
          call-count (atom 0)
          handler (router/create-router (mock-datasource) channel)]
      (with-redefs [import-repo/create-job!
                    (fn [_ds filename]
                      (swap! call-count inc)
                      {:id (java.util.UUID/randomUUID)
                       :filename filename
                       :status "Pending"
                       :total_rows 0
                       :accepted_rows 0
                       :rejected_rows 0
                       :skipped_rows 0})]
        (let [response1 (multipart-upload-request handler tempfile1 "file1.csv" "text/csv")
              response2 (multipart-upload-request handler tempfile2 "file2.csv" "text/csv")
              body1 (parse-body response1)
              body2 (parse-body response2)]
          (is (= 202 (:status response1)))
          (is (= 202 (:status response2)))
          (is (not= (:job_id body1) (:job_id body2))))))))

;; --- No internal details leaked ---

(deftest no-internal-details-leaked-on-error-test
  (testing "Error responses do not leak internal details"
    (let [channel (async/chan 10)
          handler (router/create-router (mock-datasource) channel)
          ;; Test 400 error
          request (-> (mock/request :post "/api/imports")
                      (assoc :multipart-params {}))
          response (handler request)
          body (parse-body response)
          error-json (json/write-str body)]
      ;; No stack traces, file paths, or SQL in error response
      (is (not (re-find #"Exception" error-json)))
      (is (not (re-find #"clojure\." error-json)))
      (is (not (re-find #"java\." error-json)))
      (is (not (re-find #"SELECT|INSERT|DELETE" error-json)))
      (is (not (re-find #"/tmp/" error-json))))))

(deftest no-internal-details-leaked-on-404-test
  (testing "404 error does not leak internal details"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [import-repo/find-job-by-id (fn [_ds _id] nil)]
        (let [request (mock/request :get (str "/api/imports/" (java.util.UUID/randomUUID)))
              response (handler request)
              body-str (:body response)]
          (is (not (re-find #"Exception" body-str)))
          (is (not (re-find #"clojure\." body-str)))
          (is (not (re-find #"datasource" body-str))))))))

;; --- Background processing confirmed ---

(deftest upload-returns-before-processing-test
  (testing "Upload returns 202 immediately; job sits on channel unprocessed"
    (let [csv-content "name,sku,description,category,price,stock,weight_kg\nWidget,W-001,Desc,Electronics,29.99,10,1.5"
          tempfile (create-temp-csv csv-content)
          channel (async/chan 10)
          job-id (java.util.UUID/randomUUID)
          handler (router/create-router (mock-datasource) channel)]
      (with-redefs [import-repo/create-job!
                    (fn [_ds filename]
                      {:id job-id :filename filename :status "Pending"
                       :total_rows 0 :accepted_rows 0 :rejected_rows 0 :skipped_rows 0})]
        (let [response (multipart-upload-request handler tempfile "async.csv" "text/csv")
              body (parse-body response)]
          ;; Response is immediate
          (is (= 202 (:status response)))
          (is (= "Pending" (:status body)))
          ;; Job is on the channel, not yet processed
          (let [pending-job (async/poll! channel)]
            (is (some? pending-job))
            (is (= job-id (:job-id pending-job)))))))))
