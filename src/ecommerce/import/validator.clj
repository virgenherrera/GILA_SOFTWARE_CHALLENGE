(ns ecommerce.import.validator
  "Row-level validation for CSV imports.
   Adds XSS detection, empty-row detection, and duplicate-SKU tracking
   on top of the shared Malli schemas from ecommerce.validation."
  (:require [clojure.string :as str]
            [malli.core :as mc]
            [malli.error :as me]
            [malli.util :as mu]
            [ecommerce.validation :as v]
            [ecommerce.import.parser :as parser]))

(def CSVProduct
  "Product schema for CSV imports — category is optional.
   Derived from the shared Product schema via malli.util/optional-keys
   to ensure field constraints stay in sync."
  (mu/optional-keys v/Product [:category]))

(def ^:private xss-patterns
  "Regex patterns that detect common XSS attack vectors.
   Specific enough to avoid false positives on legitimate content
   like apostrophes or generic angle brackets in product names."
  [#"(?i)<\s*script"
   #"(?i)\bon(?:error|load|click|mouseover|mouseout|focus|blur|submit|change|input|keydown|keyup|keypress|mouseenter|mouseleave|dblclick|contextmenu|abort|beforeunload|hashchange|message|resize|scroll|copy|cut|paste|drag|dragend|dragenter|dragleave|dragover|dragstart|drop|unload)\s*="
   #"(?i)javascript\s*:"])

(defn- contains-xss?
  "Returns true if the string contains any XSS pattern."
  [s]
  (when (and (string? s) (not (str/blank? s)))
    (boolean (some #(re-find % s) xss-patterns))))

(defn- detect-xss-field
  "Check all string values in a product map for XSS payloads.
   Returns the first field keyword that contains XSS, or nil if clean."
  [product-map]
  (some (fn [[k v]]
          (when (contains-xss? v)
            k))
        product-map))

(defn- blank-row?
  "Returns true if all fields in the row are blank or empty."
  [row]
  (every? #(or (nil? %) (str/blank? %)) row))

(defn validate-row
  "Validate a single CSV row. Checks in order:
   1. Blank row -> skip
   2. XSS in string fields -> reject
   3. Duplicate SKU in-file -> reject
   4. Schema validation (with category optional) -> reject if invalid
   5. Otherwise -> valid

   Arguments:
     row       - vector of CSV field strings
     seen-skus - atom containing a set of SKUs already seen in this file

   Returns:
     {:status      :blank/:valid/:invalid/:xss/:duplicate
      :product-map {...}   ; present for all except :blank
      :errors      {...}}  ; present for :invalid/:xss/:duplicate"
  [row seen-skus]
  (if (blank-row? row)
    {:status :blank}
    (let [product-map (parser/row->product-map row)
          ;; 1. Check XSS in all string fields
          xss-field (detect-xss-field product-map)]
      (if xss-field
        {:status :xss
         :product-map product-map
         :errors {xss-field "Unsafe content detected"}}
        ;; 2. Check duplicate SKU in-file
        (let [sku (:sku product-map)]
          (if (and (not (str/blank? sku))
                   (contains? @seen-skus sku))
            {:status :duplicate
             :product-map product-map
             :errors {:sku (str "Duplicate SKU in file: " sku)}}
            (do
              ;; Track non-blank SKU for in-file duplicate detection
              (when-not (str/blank? sku)
                (swap! seen-skus conj sku))
              ;; 3. Handle empty category — dissoc before schema validation
              (let [pm (if (str/blank? (:category product-map))
                         (dissoc product-map :category)
                         product-map)
                    ;; 4. Validate against CSVProduct schema (shared Malli contract)
                    errors (when-not (mc/validate CSVProduct pm)
                             (me/humanize (mc/explain CSVProduct pm)))]
                (if errors
                  {:status :invalid
                   :product-map product-map
                   :errors errors}
                  {:status :valid
                   :product-map pm})))))))))
