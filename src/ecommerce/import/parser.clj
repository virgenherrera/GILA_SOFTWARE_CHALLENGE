(ns ecommerce.import.parser
  "CSV parsing and structural validation for product imports.
   Pure functions — no side effects, no DB access."
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str]
            [ecommerce.validation :as v]))

(def ^:private expected-headers
  "Canonical CSV header columns in expected order."
  ["name" "sku" "description" "category" "price" "stock" "weight_kg"])

(def ^:private product-keys
  "Keys that map to the Product schema (excludes weight_kg)."
  [:name :sku :description :category :price :stock])

(defn validate-headers
  "Validate that the CSV header row matches expected columns.
   Returns nil if valid, or an error string if invalid."
  [header-row]
  (let [normalized (mapv str/trim header-row)]
    (when (not= normalized expected-headers)
      (str "Invalid CSV headers. Expected: " (str/join ", " expected-headers)))))

(defn- blank-row?
  "Returns true if all fields in the row are blank or empty."
  [row]
  (every? #(or (nil? %) (str/blank? %)) row))

(defn- coerce-price
  "Coerce a string price value to double. Returns nil on failure."
  [s]
  (when-not (str/blank? s)
    (try
      (Double/parseDouble (str/trim s))
      (catch NumberFormatException _ nil))))

(defn- coerce-stock
  "Coerce a string stock value to int. Returns nil on failure."
  [s]
  (when-not (str/blank? s)
    (try
      (let [trimmed (str/trim s)
            d (Double/parseDouble trimmed)]
        (when (== d (Math/floor d))
          (int d)))
      (catch NumberFormatException _ nil))))

(defn row->product-map
  "Convert a CSV data row (vector of strings) to a product map.
   Applies type coercion for price and stock.
   Returns the map (validation is done separately)."
  [row]
  (let [fields (mapv str/trim row)
        raw-map (zipmap (mapv keyword expected-headers) fields)
        ;; Build product map with coerced types, excluding weight_kg
        product (-> (select-keys raw-map product-keys)
                    (update :name str/trim)
                    (update :sku str/trim)
                    (update :category str/trim))]
    ;; Coerce numeric fields
    (-> product
        (assoc :price (coerce-price (:price product)))
        (assoc :stock (coerce-stock (:stock product)))
        ;; Remove nil description (optional field — keep only if non-blank)
        (cond-> (str/blank? (:description product)) (dissoc :description)))))

(defn validate-row
  "Validate a product map against the Product schema.
   Returns nil if valid, or a map of humanized errors if invalid."
  [product-map]
  (v/validate v/Product product-map))

(defn parse-csv
  "Parse a CSV string into a sequence of raw rows (vectors of strings).
   Returns {:headers [...] :rows [...]} or {:error \"...\"}."
  [csv-content]
  (try
    (let [rows (csv/read-csv csv-content)]
      (if (empty? rows)
        {:error "CSV file is empty"}
        (let [header (first rows)
              header-error (validate-headers header)]
          (if header-error
            {:error header-error}
            {:headers header
             :rows (rest rows)}))))
    (catch Exception _
      {:error "Failed to parse CSV content"})))

(defn classify-row
  "Classify a data row. Returns one of:
   :blank — row is all blanks (skip silently)
   :valid — row passes Product schema validation
   :invalid — row fails validation (errors attached)

   Return shape: {:classification :valid/:invalid/:blank
                  :product-map {...}   ; only for :valid/:invalid
                  :errors {...}        ; only for :invalid}"
  [row]
  (if (blank-row? row)
    {:classification :blank}
    (let [product-map (row->product-map row)
          errors (validate-row product-map)]
      (if errors
        {:classification :invalid
         :product-map product-map
         :errors errors}
        {:classification :valid
         :product-map product-map}))))
