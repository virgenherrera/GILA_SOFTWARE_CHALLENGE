(ns ecommerce.import.handler
  "HTTP handlers for CSV import endpoints.
   POST /api/imports — multipart/form-data upload
   GET  /api/imports/:id — job status lookup
   GET  /api/imports/:id/errors — paginated error listing"
  (:require [clojure.core.async :as async]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ecommerce.import.error-repository :as error-repo]
            [ecommerce.import.repository :as repo])
  (:import [java.util UUID]))

(defn- json-value-fn
  "Custom value function for data.json to handle DB timestamp types."
  [_key value]
  (cond
    (instance? java.time.OffsetDateTime value) (str value)
    (instance? java.sql.Timestamp value) (str value)
    :else value))

(defn- csv-file?
  "Returns true if the uploaded file looks like a CSV based on filename or content-type."
  [{:keys [filename content-type]}]
  (or (and (string? filename)
           (str/ends-with? (str/lower-case filename) ".csv"))
      (and (string? content-type)
           (str/includes? (str/lower-case content-type) "text/csv"))))

(defn- try-parse-uuid
  "Parse a string as UUID. Returns nil on invalid format."
  [s]
  (try
    (UUID/fromString s)
    (catch Exception _ nil)))

(defn upload-csv
  "Upload CSV handler. Takes datasource and async channel, returns a Ring handler.
   Validates the uploaded file, creates a job record, and enqueues for processing."
  [datasource channel]
  (fn [request]
    (let [multipart-params (:multipart-params request)
          file-data (get multipart-params "file")]
      (cond
        ;; No file uploaded
        (nil? file-data)
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (json/write-str
                {:error {:code "VALIDATION_ERROR"
                         :message "Missing required file field"}})}

        ;; Not a CSV file
        (not (csv-file? file-data))
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (json/write-str
                {:error {:code "VALIDATION_ERROR"
                         :message "File must be a CSV file"}})}

        :else
        (try
          (let [tempfile (:tempfile file-data)
                filename (:filename file-data)
                csv-content (slurp tempfile)
                ;; Create the job record
                job (repo/create-job! datasource filename)
                job-id (:id job)]
            ;; Enqueue job for background processing
            (async/put! channel {:job-id job-id :csv-content csv-content})
            (log/info "Import job created:" job-id "for file:" filename)
            {:status 202
             :headers {"Content-Type" "application/json"}
             :body (json/write-str
                    {:job_id (str job-id)
                     :status "Pending"
                     :message (str "Import job created. Poll /api/imports/" job-id " for status")}
                    :value-fn json-value-fn)})
          (catch Exception e
            (log/error e "Failed to create import job")
            {:status 500
             :headers {"Content-Type" "application/json"}
             :body (json/write-str
                    {:error {:code "INTERNAL_ERROR"
                             :message "Failed to process upload"}})}))))))

(defn get-job-status
  "Get import job status handler. Takes datasource, returns a Ring handler.
   Looks up the job by UUID path parameter."
  [datasource]
  (fn [request]
    (let [id-str (-> request :path-params :id)
          job-id (try-parse-uuid id-str)]
      (if (nil? job-id)
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (json/write-str
                {:error {:code "VALIDATION_ERROR"
                         :message "Invalid job ID format"}})}
        (let [job (repo/find-job-by-id datasource job-id)]
          (if job
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body (json/write-str job :value-fn json-value-fn)}
            {:status 404
             :headers {"Content-Type" "application/json"}
             :body (json/write-str
                    {:error {:code "NOT_FOUND"
                             :message "Import job not found"}})}))))))

;; --- Import error helpers ---

(def ^:private default-page 1)
(def ^:private default-per-page 20)
(def ^:private max-per-page 100)

(defn- parse-int
  "Parse a string to an integer. Returns nil on failure."
  [s]
  (when (and s (string? s) (re-matches #"-?\d+" s))
    (try (Integer/parseInt s)
         (catch NumberFormatException _ nil))))

(defn- sanitize-html
  "HTML-encode angle brackets and ampersands to prevent XSS."
  [s]
  (when s
    (-> s
        (str/replace "&" "&amp;")
        (str/replace "<" "&lt;")
        (str/replace ">" "&gt;"))))

(defn- error->api-item
  "Map a DB import_errors row to the API response shape."
  [error]
  {:row_number   (:row_number error)
   :raw_row_data (sanitize-html (:value error))
   :field_name   (:field error)
   :error_reason (:message error)
   :product_sku  nil})

(defn- build-error-paging-urls
  "Build prev/next URLs for the import errors endpoint.
   Simpler than product search — only page and perPage params."
  [base-path page per-page total]
  (let [last-page (max 1 (long (Math/ceil (/ (double total) per-page))))
        build-url (fn [p]
                    (str base-path "?page=" p "&perPage=" per-page))]
    {:prev (when (> page 1) (build-url (dec page)))
     :next (when (< page last-page) (build-url (inc page)))}))

(defn get-job-errors
  "Get paginated import errors for a job.
   Takes datasource, returns a Ring handler."
  [datasource]
  (fn [request]
    (let [id-str (-> request :path-params :id)
          job-id (try-parse-uuid id-str)]
      (if (nil? job-id)
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (json/write-str
                {:error {:code "VALIDATION_ERROR"
                         :message "Invalid job ID format"}})}
        (let [job (repo/find-job-by-id datasource job-id)]
          (if (nil? job)
            {:status 404
             :headers {"Content-Type" "application/json"}
             :body (json/write-str
                    {:error {:code "NOT_FOUND"
                             :message "Import job not found"}})}
            (let [query-params (:query-params request)
                  raw-page     (get query-params "page")
                  raw-per-page (get query-params "perPage")
                  page         (if raw-page (parse-int raw-page) default-page)
                  per-page     (if raw-per-page (parse-int raw-per-page) default-per-page)]
              (if (or (nil? page) (< page 1)
                      (nil? per-page) (< per-page 1) (> per-page max-per-page))
                {:status 400
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str
                        {:error {:code "VALIDATION_ERROR"
                                 :message "Invalid pagination parameters"}})}
                (let [errors (error-repo/list-errors-by-job datasource job-id page per-page)
                      total  (error-repo/count-errors-by-job datasource job-id)
                      base-path (str "/api/imports/" job-id "/errors")
                      paging-urls (build-error-paging-urls base-path page per-page total)]
                  {:status 200
                   :headers {"Content-Type" "application/json"}
                   :body (json/write-str
                          {:items (mapv error->api-item errors)
                           :paging (merge {:page    page
                                           :perPage per-page
                                           :total   total}
                                          paging-urls)}
                          :value-fn json-value-fn)})))))))))
