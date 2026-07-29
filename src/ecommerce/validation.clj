(ns ecommerce.validation
  (:require [malli.core :as mc]
            [malli.error :as me]))

(def Product
  "Malli schema for a product entity."
  [:map {:closed true}
   [:sku [:string {:min 1 :max 50}]]
   [:name [:string {:min 1 :max 255}]]
   [:description {:optional true} [:string {:max 2000}]]
   [:price [:and
            [:double {:min 0.01}]
            [:fn {:error/message "price must be <= 99999.99"}
             #(<= % 99999.99)]]]
   [:category {:optional true} [:string {:min 1 :max 100}]]
   [:stock [:int {:min 0}]]
   [:weight_kg {:optional true} [:and
                                  [:double {:min 0}]
                                  [:fn {:error/message "weight must be <= 9999.99"}
                                   #(<= % 9999.99)]]]])

(def UpdateProduct
  "Malli schema for updating a product (no SKU — comes from URL)."
  [:map {:closed true}
   [:name [:string {:min 1 :max 255}]]
   [:description {:optional true} [:string {:max 2000}]]
   [:price [:and
            [:double {:min 0.01}]
            [:fn {:error/message "price must be <= 99999.99"}
             #(<= % 99999.99)]]]
   [:category {:optional true} [:string {:min 1 :max 100}]]
   [:stock [:int {:min 0}]]
   [:weight_kg {:optional true} [:and
                                  [:double {:min 0}]
                                  [:fn {:error/message "weight must be <= 9999.99"}
                                   #(<= % 9999.99)]]]])

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
