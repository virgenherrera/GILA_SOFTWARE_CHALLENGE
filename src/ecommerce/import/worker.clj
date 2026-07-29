(ns ecommerce.import.worker
  "Background worker that processes CSV import jobs from a core.async channel.
   Reads jobs, parses CSV content, validates rows via the import validator,
   and upserts products."
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [ecommerce.import.parser :as parser]
            [ecommerce.import.validator :as validator]
            [ecommerce.import.repository :as import-repo]
            [ecommerce.product.repository :as product-repo]))

(defn- record-validation-errors!
  "Record import_errors entries for a row that failed schema validation."
  [datasource job-id row-number raw-row errors]
  (let [raw-row-str (str/join "," raw-row)]
    (doseq [[field messages] errors]
      (doseq [message (if (sequential? messages) messages [messages])]
        (import-repo/record-import-error!
         datasource
         {:job_id     job-id
          :row_number row-number
          :field      (name field)
          :value      raw-row-str
          :error_code "VALIDATION_ERROR"
          :message    (str message)})))))

(defn- record-rejection!
  "Record import_errors entries for a rejected row (XSS or duplicate SKU)."
  [datasource job-id row-number raw-row errors error-code]
  (let [raw-row-str (str/join "," raw-row)]
    (doseq [[field message] errors]
      (import-repo/record-import-error!
       datasource
       {:job_id     job-id
        :row_number row-number
        :field      (if (keyword? field) (name field) (str field))
        :value      raw-row-str
        :error_code error-code
        :message    (str message)}))))

(defn- record-insert-error!
  "Record an import_errors entry for a row that failed during DB insertion."
  [datasource job-id row-number raw-row error-message]
  (import-repo/record-import-error!
   datasource
   {:job_id     job-id
    :row_number row-number
    :field      nil
    :value      (str/join "," raw-row)
    :error_code "INSERT_ERROR"
    :message    error-message}))

(defn- upsert-product!
  "Try to insert a product. On duplicate SKU, attempt an update instead.
   Returns true on success, false on failure."
  [datasource product-map]
  (try
    (product-repo/insert-product! datasource product-map)
    true
    (catch clojure.lang.ExceptionInfo e
      (if (= 409 (:status (ex-data e)))
        ;; Duplicate SKU — update instead
        (let [sku (:sku product-map)
              update-map (dissoc product-map :sku)]
          (product-repo/update-product! datasource sku update-map)
          true)
        (throw e)))))

(defn- process-job
  "Process a single import job: parse CSV, validate rows, upsert products."
  [datasource {:keys [job-id csv-content]}]
  (log/info "Processing import job" job-id)
  (import-repo/update-job-processing! datasource job-id)
  (let [parse-result (parser/parse-csv csv-content)]
    (if (:error parse-result)
      ;; CSV parsing/structure failed entirely
      (do
        (log/warn "Import job" job-id "failed:" (:error parse-result))
        (import-repo/record-import-error!
         datasource
         {:job_id     job-id
          :row_number 1
          :field      nil
          :value      nil
          :error_code "PARSE_ERROR"
          :message    (:error parse-result)})
        (import-repo/update-job-completed!
         datasource job-id "Failed" 0 0 1 0))
      ;; Parse succeeded — process rows with validator
      (let [rows (:rows parse-result)
            seen-skus (atom #{})
            results (reduce
                     (fn [acc [idx raw-row]]
                       ;; row-number is 1-indexed, header is row 1, first data row is row 2
                       (let [row-number (+ idx 2)
                             result (validator/validate-row raw-row seen-skus)]
                         (case (:status result)
                           :blank
                           (update acc :skipped inc)

                           :xss
                           (do
                             (record-rejection!
                              datasource job-id row-number raw-row
                              (:errors result) "XSS_ERROR")
                             (update acc :rejected inc))

                           :duplicate
                           (do
                             (record-rejection!
                              datasource job-id row-number raw-row
                              (:errors result) "DUPLICATE_SKU")
                             (update acc :rejected inc))

                           :invalid
                           (do
                             (record-validation-errors!
                              datasource job-id row-number raw-row (:errors result))
                             (update acc :rejected inc))

                           :valid
                           (try
                             (upsert-product! datasource (:product-map result))
                             (update acc :accepted inc)
                             (catch Exception e
                               (log/warn e "Failed to insert row" row-number "in job" job-id)
                               (record-insert-error!
                                datasource job-id row-number raw-row
                                (or (.getMessage e) "Unknown insert error"))
                               (update acc :rejected inc))))))
                     {:accepted 0 :rejected 0 :skipped 0}
                     (map-indexed vector rows))
            total (+ (:accepted results) (:rejected results))
            status (cond
                     (zero? total)               "Completed"
                     (zero? (:accepted results)) "CompletedWithErrors"
                     (pos? (:rejected results))  "CompletedWithErrors"
                     :else                       "Completed")]
        (import-repo/update-job-completed!
         datasource job-id status
         total (:accepted results) (:rejected results) (:skipped results))
        (log/info "Import job" job-id "completed:"
                  "accepted=" (:accepted results)
                  "rejected=" (:rejected results)
                  "skipped=" (:skipped results))))))

(defn start-worker
  "Start a background thread that takes jobs from `channel` and processes them.
   Each job is a map with :job-id and :csv-content keys.
   Uses a real thread (not go-loop) because `process-job` performs synchronous
   JDBC calls — running that on the fixed-size go-block thread pool can starve
   it and deadlock when multiple imports run concurrently.
   Returns the channel (caller closes it to stop the worker)."
  [datasource channel]
  (async/thread
    (loop []
      (when-let [job (async/<!! channel)]
        (try
          (process-job datasource job)
          (catch Exception e
            (log/error e "Unhandled error processing import job" (:job-id job))
            ;; Try to mark the job as Failed
            (try
              (import-repo/update-job-completed!
               datasource (:job-id job) "Failed" 0 0 0 0)
              (catch Exception inner
                (log/error inner "Failed to mark job as Failed" (:job-id job))))))
        (recur))))
  channel)
