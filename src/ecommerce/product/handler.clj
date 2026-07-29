(ns ecommerce.product.handler
  (:require [ecommerce.product.repository :as repo]
            [ecommerce.validation :as v]
            [clojure.data.json :as json]
            [clojure.string :as str]))

(defn- prepare-product
  "Prepare raw input for validation: trim name, coerce price to double."
  [params]
  (cond-> params
    (string? (:name params)) (update :name str/trim)
    (number? (:price params)) (update :price double)))

(defn- validation-errors->details
  "Transform Malli humanized errors into API detail entries.
   Each entry has :field and :reason keys."
  [errors]
  (reduce-kv (fn [acc field messages]
               (into acc (map (fn [msg] {:field (name field) :reason msg})
                              messages)))
             []
             errors))

(defn- json-value-fn
  "Custom value function for data.json to handle DB timestamp types."
  [_key value]
  (cond
    (instance? java.time.OffsetDateTime value) (str value)
    (instance? java.sql.Timestamp value) (str value)
    :else value))

(defn create-product
  "Create-product handler. Takes datasource, returns a Ring handler function (closure pattern)."
  [datasource]
  (fn [request]
    (let [raw-params (:body-params request)
          params (prepare-product (if (map? raw-params) raw-params {}))
          errors (v/validate v/Product params)]
      (if errors
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (json/write-str
                {:error {:code "VALIDATION_ERROR"
                         :message "Product validation failed"
                         :details (validation-errors->details errors)}})}
        (let [product (repo/insert-product! datasource params)]
          {:status 201
           :headers {"Content-Type" "application/json"}
           :body (json/write-str product :value-fn json-value-fn)})))))
