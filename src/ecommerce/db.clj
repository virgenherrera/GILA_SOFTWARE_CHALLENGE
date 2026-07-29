(ns ecommerce.db
  (:require [next.jdbc :as jdbc]
            [clojure.tools.logging :as log])
  (:import [com.zaxxer.hikari HikariDataSource HikariConfig]))

(defn create-datasource
  "Create a HikariCP connection pool from the config map.
   Uses lazy initialization so the app starts even if the DB is temporarily unavailable."
  [{:keys [db]}]
  (let [config (doto (HikariConfig.)
                 (.setJdbcUrl (format "jdbc:postgresql://%s:%d/%s"
                                      (:host db) (:port db) (:dbname db)))
                 (.setUsername (:user db))
                 (.setPassword (:password db))
                 (.setInitializationFailTimeout -1)
                 (.setConnectionTimeout 5000)
                 (.setMaximumPoolSize 10)
                 (.setPoolName "ecommerce-pool"))]
    (log/info "Creating HikariCP datasource")
    (HikariDataSource. config)))

(defn close-datasource
  "Close the HikariCP datasource."
  [^HikariDataSource ds]
  (when ds
    (log/info "Closing HikariCP datasource")
    (.close ds)))

(defmacro with-transaction
  "Execute body within a database transaction.
   Binds the transaction connection to `tx-sym`.

   Usage:
     (with-transaction [tx datasource]
       (jdbc/execute! tx [...]))"
  [[tx-sym datasource] & body]
  `(jdbc/with-transaction [~tx-sym ~datasource]
     ~@body))
