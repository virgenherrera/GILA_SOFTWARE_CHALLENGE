(ns ecommerce.product.search
  "Pure query builder for product search. No DB calls — only HoneySQL data structures."
  (:require [clojure.string :as str])
  (:import [java.net URLEncoder]))

(def ^:private select-columns
  [:sku :name :description :price :category :stock :created_at :updated_at])

(def ^:private valid-sort-fields #{"name" "price" "stock"})
(def ^:private valid-sort-orders #{"asc" "desc"})

(def ^:private default-page 1)
(def ^:private default-per-page 20)
(def ^:private max-per-page 100)

(defn- parse-int
  "Parse a string to an integer. Returns nil on failure."
  [s]
  (when (and s (string? s) (re-matches #"-?\d+" s))
    (try (Integer/parseInt s)
         (catch NumberFormatException _ nil))))

(defn- parse-decimal
  "Parse a string to a BigDecimal. Returns nil on failure."
  [s]
  (when (and s (string? s) (re-matches #"-?\d+(\.\d+)?" s))
    (try (BigDecimal. ^String s)
         (catch NumberFormatException _ nil))))

(defn parse-search-params
  "Parse raw query-param string map to typed values with defaults.
   Returns a map with parsed values and :_raw for validation."
  [query-params]
  (let [qp (or query-params {})
        raw-page (get qp "page")
        raw-per-page (get qp "perPage")
        raw-q (get qp "q")
        raw-category (get qp "category")
        raw-price-min (get qp "priceMin")
        raw-price-max (get qp "priceMax")
        raw-sort-by (get qp "sortBy")
        raw-sort-order (get qp "sortOrder")]
    {:page (if raw-page (parse-int raw-page) default-page)
     :per-page (if raw-per-page (parse-int raw-per-page) default-per-page)
     :q (when (and raw-q (not (str/blank? raw-q))) (str/trim raw-q))
     :category (when (and raw-category (not (str/blank? raw-category)))
                 (str/trim raw-category))
     :price-min (when raw-price-min (parse-decimal raw-price-min))
     :price-max (when raw-price-max (parse-decimal raw-price-max))
     :sort-by (when (and raw-sort-by (not (str/blank? raw-sort-by)))
                (str/lower-case (str/trim raw-sort-by)))
     :sort-order (when (and raw-sort-order (not (str/blank? raw-sort-order)))
                   (str/lower-case (str/trim raw-sort-order)))
     :_raw {:page raw-page
            :perPage raw-per-page
            :priceMin raw-price-min
            :priceMax raw-price-max
            :sortBy raw-sort-by
            :sortOrder raw-sort-order}}))

(defn validate-search-params
  "Validate parsed search params.
   Returns {:params <clean-map>} on success or {:errors [<details>]} on failure."
  [parsed]
  (let [raw (:_raw parsed)
        errors (cond-> []
                 (and (:page raw)
                      (or (nil? (:page parsed))
                          (< (:page parsed) 1)))
                 (conj {:field "page" :reason "must be a positive integer"})

                 (and (:perPage raw)
                      (or (nil? (:per-page parsed))
                          (< (:per-page parsed) 1)
                          (> (:per-page parsed) max-per-page)))
                 (conj {:field "perPage"
                        :reason (str "must be between 1 and " max-per-page)})

                 (and (:priceMin raw) (nil? (:price-min parsed)))
                 (conj {:field "priceMin"
                        :reason "must be a valid decimal number"})

                 (and (:priceMax raw) (nil? (:price-max parsed)))
                 (conj {:field "priceMax"
                        :reason "must be a valid decimal number"})

                 (and (:price-min parsed) (:price-max parsed)
                      (pos? (.compareTo ^BigDecimal (:price-min parsed)
                                        ^BigDecimal (:price-max parsed))))
                 (conj {:field "priceMin"
                        :reason "must be less than or equal to priceMax"})

                 (and (:sortBy raw)
                      (not (valid-sort-fields (:sort-by parsed))))
                 (conj {:field "sortBy"
                        :reason (str "must be one of: "
                                     (str/join ", " (sort valid-sort-fields)))})

                 (and (:sortOrder raw)
                      (not (valid-sort-orders (:sort-order parsed))))
                 (conj {:field "sortOrder"
                        :reason "must be one of: asc, desc"}))]
    (if (seq errors)
      {:errors errors}
      {:params (dissoc parsed :_raw)})))

(defn- build-where-clauses
  "Build WHERE clause from validated params. Returns nil if no filters.
   Uses [:lift val] inside [:raw ...] for parameterized tsvector queries."
  [params]
  (let [clauses (cond-> []
                  (:q params)
                  (conj [:raw ["search_vector @@ plainto_tsquery('english', "
                               [:lift (:q params)] ")"]])

                  (:category params)
                  (conj [:= :category (:category params)])

                  (:price-min params)
                  (conj [:>= :price (:price-min params)])

                  (:price-max params)
                  (conj [:<= :price (:price-max params)]))]
    (case (count clauses)
      0 nil
      1 (first clauses)
      (into [:and] clauses))))

(defn- build-order-by
  "Determine ORDER BY clause based on search params.
   - q present without sortBy → ts_rank DESC (relevance)
   - sortBy present → sortBy column + sortOrder
   - default → name ASC"
  [params]
  (cond
    (:sort-by params)
    [[(keyword (:sort-by params)) (keyword (or (:sort-order params) "asc"))]]

    (:q params)
    [[[:raw ["ts_rank(search_vector, plainto_tsquery('english', "
             [:lift (:q params)] "))"]]
      :desc]]

    :else
    [[:name :asc]]))

(defn build-search-query
  "Build the main SELECT query with all filters, sort, and pagination.
   Takes validated params, returns a HoneySQL map."
  [params]
  (let [per-page (:per-page params)
        page (:page params)
        offset (* (dec page) per-page)
        where-clause (build-where-clauses params)
        order-by (build-order-by params)]
    (cond-> {:select select-columns
             :from [:products]
             :order-by order-by
             :limit per-page
             :offset offset}
      where-clause (assoc :where where-clause))))

(defn build-count-query
  "Build COUNT(*) query with same filters but no sort/limit/offset.
   Takes validated params, returns a HoneySQL map."
  [params]
  (let [where-clause (build-where-clauses params)]
    (cond-> {:select [[[:raw "count(*)"] :total]]
             :from [:products]}
      where-clause (assoc :where where-clause))))

(defn- encode-param
  "URL-encode a parameter value."
  [v]
  (URLEncoder/encode (str v) "UTF-8"))

(defn build-paging-urls
  "Construct prev/next relative URLs preserving all query params.
   Returns {:prev <url-or-nil> :next <url-or-nil>}."
  [base-path params total]
  (let [page (:page params)
        per-page (:per-page params)
        last-page (max 1 (long (Math/ceil (/ (double total) per-page))))
        build-url (fn [p]
                    (let [pairs (cond-> [(str "page=" p)
                                         (str "perPage=" per-page)]
                                  (:q params)
                                  (conj (str "q=" (encode-param (:q params))))

                                  (:category params)
                                  (conj (str "category="
                                             (encode-param (:category params))))

                                  (:price-min params)
                                  (conj (str "priceMin="
                                             (.toPlainString
                                              ^BigDecimal (:price-min params))))

                                  (:price-max params)
                                  (conj (str "priceMax="
                                             (.toPlainString
                                              ^BigDecimal (:price-max params))))

                                  (:sort-by params)
                                  (conj (str "sortBy=" (:sort-by params)))

                                  (:sort-order params)
                                  (conj (str "sortOrder="
                                             (:sort-order params))))]
                      (str base-path "?" (str/join "&" pairs))))]
    {:prev (when (> page 1) (build-url (dec page)))
     :next (when (< page last-page) (build-url (inc page)))}))
