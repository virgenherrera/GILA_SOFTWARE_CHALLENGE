(ns ecommerce.validation
  (:require [malli.core :as mc]
            [malli.error :as me]))

(def Product
  "Malli schema for a product entity."
  [:map {:closed true}
   [:sku [:string {:min 1}]]
   [:name [:string {:min 1 :max 256}]]
   [:description {:optional true} [:string {:max 4096}]]
   [:price [:and
            [:double {:min 0.01}]
            [:fn {:error/message "price must be <= 99999.99"}
             #(<= % 99999.99)]]]
   [:category [:string {:min 1 :max 100}]]
   [:stock [:int {:min 0}]]])

(defn validate
  "Validate data against a Malli schema.
   Returns nil if valid, or a map of humanized errors if invalid."
  [schema data]
  (when-not (mc/validate schema data)
    (me/humanize (mc/explain schema data))))

(defn explain-errors
  "Return a detailed explanation of validation errors.
   Returns nil if valid, or the raw Malli explain output."
  [schema data]
  (when-not (mc/validate schema data)
    (mc/explain schema data)))
