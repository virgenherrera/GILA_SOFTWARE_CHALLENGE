(ns ecommerce.config
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]))

(defn- env
  "Read an environment variable, returning nil if not set."
  [var-name]
  (System/getenv var-name))

(defn- require-env!
  "Read an environment variable and fail if missing or blank."
  [var-name]
  (let [value (env var-name)]
    (when (or (nil? value) (str/blank? value))
      (log/error (str "Missing or invalid environment variable: " var-name))
      (System/exit 1))
    value))

(defn- parse-port!
  "Parse a string as an integer port (1-65535). Exit on failure."
  [var-name value]
  (let [n (try (Integer/parseInt value)
               (catch NumberFormatException _
                 (log/error (str "Missing or invalid environment variable: " var-name))
                 (System/exit 1)))]
    (when (or (< n 1) (> n 65535))
      (log/error (str "Missing or invalid environment variable: " var-name))
      (System/exit 1))
    n))

(defn load-config
  "Load and validate all configuration from environment variables.
   Returns a config map or exits the process on validation failure."
  []
  (let [db-host     (require-env! "DB_HOST")
        db-port-str (require-env! "DB_PORT")
        db-name     (require-env! "DB_NAME")
        db-user     (require-env! "DB_USER")
        db-password (require-env! "DB_PASSWORD")
        cookie-secret (require-env! "COOKIE_SECRET")
        port-str    (or (env "PORT") "3000")
        log-level   (or (env "LOG_LEVEL") "INFO")
        db-port     (parse-port! "DB_PORT" db-port-str)
        port        (parse-port! "PORT" port-str)]
    {:db {:host     db-host
          :port     db-port
          :dbname   db-name
          :user     db-user
          :password db-password}
     :server {:port port}
     :log-level log-level
     :cookie-secret cookie-secret}))
