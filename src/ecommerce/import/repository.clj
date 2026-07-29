(ns ecommerce.import.repository
  "CRUD operations for csv_import_jobs and import_errors tables."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as hsql]))

(defn create-job!
  "Insert a new csv_import_jobs record with the given filename.
   Returns the created row with all columns."
  [datasource filename]
  (let [query (hsql/format {:insert-into :csv_import_jobs
                            :values [{:filename filename}]
                            :returning [:*]})]
    (jdbc/execute-one! datasource query
                       {:builder-fn rs/as-unqualified-maps})))

(defn find-job-by-id
  "Find a csv_import_jobs record by UUID. Returns nil if not found."
  [datasource job-id]
  (let [query (hsql/format {:select [:*]
                            :from [:csv_import_jobs]
                            :where [:= :id job-id]})]
    (jdbc/execute-one! datasource query
                       {:builder-fn rs/as-unqualified-maps})))

(defn update-job-processing!
  "Set job status to 'Processing' and update timestamp."
  [datasource job-id]
  (let [query (hsql/format {:update :csv_import_jobs
                            :set {:status "Processing"
                                  :updated_at [:raw "now()"]}
                            :where [:= :id job-id]})]
    (jdbc/execute-one! datasource query)))

(defn update-job-completed!
  "Set final job status with row counts."
  [datasource job-id status total-rows accepted-rows rejected-rows skipped-rows]
  (let [query (hsql/format {:update :csv_import_jobs
                            :set {:status status
                                  :total_rows total-rows
                                  :accepted_rows accepted-rows
                                  :rejected_rows rejected-rows
                                  :skipped_rows skipped-rows
                                  :updated_at [:raw "now()"]}
                            :where [:= :id job-id]})]
    (jdbc/execute-one! datasource query)))

(defn record-import-error!
  "Insert an import_errors record for a failed row."
  [datasource error-map]
  (let [query (hsql/format {:insert-into :import_errors
                            :values [(select-keys error-map
                                                  [:job_id :row_number :field
                                                   :value :error_code :message])]})]
    (jdbc/execute-one! datasource query)))
