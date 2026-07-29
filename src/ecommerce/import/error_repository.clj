(ns ecommerce.import.error-repository
  "Query functions for import_errors table — paginated listing and counting."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as hsql]))

(defn list-errors-by-job
  "Fetch paginated import errors for a given job, ordered by row_number ASC.
   Returns a vector of maps with unqualified keys."
  [datasource job-id page per-page]
  (let [offset (* (dec page) per-page)
        query (hsql/format {:select [:row_number :field :value :message]
                            :from [:import_errors]
                            :where [:= :job_id job-id]
                            :order-by [[:row_number :asc]]
                            :limit per-page
                            :offset offset})]
    (jdbc/execute! datasource query
                   {:builder-fn rs/as-unqualified-maps})))

(defn count-errors-by-job
  "Count total import errors for a given job."
  [datasource job-id]
  (let [query (hsql/format {:select [[[:raw "count(*)"] :total]]
                            :from [:import_errors]
                            :where [:= :job_id job-id]})]
    (:total (jdbc/execute-one! datasource query
                               {:builder-fn rs/as-unqualified-maps}))))
