(ns ecommerce.middleware
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [ring.middleware.params :as params]))

(defn wrap-error-handling
  "Catches all exceptions and returns a JSON error response."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)
              status (or (:status data) 500)
              code   (or (:code data) "INTERNAL_ERROR")
              msg    (or (.getMessage e) "Internal server error")]
          (log/warn e "Handled exception" {:code code :status status})
          {:status status
           :headers {"Content-Type" "application/json"}
           :body (json/write-str {:error {:code code :message msg}})}))
      (catch Exception e
        (log/error e "Unhandled exception")
        {:status 500
         :headers {"Content-Type" "application/json"}
         :body (json/write-str {:error {:code "INTERNAL_ERROR"
                                        :message "Internal server error"}})}))))

(defn wrap-security-headers
  "Adds security headers to every response."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "X-Content-Type-Options"] "nosniff")
          (assoc-in [:headers "X-Frame-Options"] "DENY")
          (assoc-in [:headers "X-XSS-Protection"] "0")
          (assoc-in [:headers "Strict-Transport-Security"] "max-age=31536000")))))

(def muuntaja-instance
  "Muuntaja instance configured for JSON content negotiation."
  (m/create
   (-> m/default-options
       (assoc-in [:formats "application/json" :encoder-opts] {})
       (assoc-in [:formats "application/json" :decoder-opts] {}))))

(defn wrap-content-type
  "Wraps handler with muuntaja content negotiation (JSON format/negotiate/response)."
  [handler]
  (-> handler
      (middleware/wrap-format muuntaja-instance)))

(defn wrap-middleware
  "Apply the full middleware stack to a handler.
   Order (outermost first): error-handling -> security-headers -> params -> content-type."
  [handler]
  (-> handler
      params/wrap-params
      wrap-content-type
      wrap-security-headers
      wrap-error-handling))
