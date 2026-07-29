(ns ecommerce.product.search-test
  (:require [clojure.test :refer [deftest is testing]]
            [ecommerce.product.search :as search]
            [honey.sql :as hsql]
            [clojure.string :as str]))

;; ============================================================
;; parse-search-params
;; ============================================================

(deftest parse-defaults-when-no-params-test
  (testing "Empty params map returns all defaults"
    (let [result (search/parse-search-params {})]
      (is (= 1 (:page result)))
      (is (= 20 (:per-page result)))
      (is (nil? (:q result)))
      (is (nil? (:category result)))
      (is (nil? (:price-min result)))
      (is (nil? (:price-max result)))
      (is (nil? (:sort-by result)))
      (is (nil? (:sort-order result))))))

(deftest parse-nil-params-test
  (testing "nil params map returns all defaults"
    (let [result (search/parse-search-params nil)]
      (is (= 1 (:page result)))
      (is (= 20 (:per-page result))))))

(deftest parse-valid-page-test
  (testing "Valid page string is parsed to integer"
    (let [result (search/parse-search-params {"page" "3"})]
      (is (= 3 (:page result))))))

(deftest parse-valid-per-page-test
  (testing "Valid perPage string is parsed to integer"
    (let [result (search/parse-search-params {"perPage" "50"})]
      (is (= 50 (:per-page result))))))

(deftest parse-invalid-page-returns-nil-test
  (testing "Non-numeric page string returns nil (for validation to catch)"
    (let [result (search/parse-search-params {"page" "abc"})]
      (is (nil? (:page result))))))

(deftest parse-q-trims-whitespace-test
  (testing "Query param q is trimmed"
    (let [result (search/parse-search-params {"q" "  running shoes  "})]
      (is (= "running shoes" (:q result))))))

(deftest parse-blank-q-returns-nil-test
  (testing "Blank query param q returns nil"
    (let [result (search/parse-search-params {"q" "   "})]
      (is (nil? (:q result))))))

(deftest parse-decimal-params-test
  (testing "priceMin and priceMax parsed to BigDecimal"
    (let [result (search/parse-search-params {"priceMin" "10.50"
                                              "priceMax" "99.99"})]
      (is (= (BigDecimal. "10.50") (:price-min result)))
      (is (= (BigDecimal. "99.99") (:price-max result))))))

(deftest parse-invalid-decimal-returns-nil-test
  (testing "Invalid decimal strings return nil"
    (let [result (search/parse-search-params {"priceMin" "abc"})]
      (is (nil? (:price-min result))))))

(deftest parse-sort-by-lowercased-test
  (testing "sortBy is lowercased"
    (let [result (search/parse-search-params {"sortBy" "Price"})]
      (is (= "price" (:sort-by result))))))

(deftest parse-sort-order-lowercased-test
  (testing "sortOrder is lowercased"
    (let [result (search/parse-search-params {"sortOrder" "DESC"})]
      (is (= "desc" (:sort-order result))))))

;; ============================================================
;; validate-search-params
;; ============================================================

(deftest validate-valid-params-test
  (testing "Valid params return :params without :_raw"
    (let [parsed (search/parse-search-params {"page" "2" "perPage" "10"
                                              "q" "shoes"
                                              "category" "Footwear"
                                              "priceMin" "10" "priceMax" "100"
                                              "sortBy" "price"
                                              "sortOrder" "desc"})
          result (search/validate-search-params parsed)]
      (is (contains? result :params))
      (is (not (contains? result :errors)))
      (is (not (contains? (:params result) :_raw))))))

(deftest validate-no-params-succeeds-test
  (testing "No params at all is valid (uses defaults)"
    (let [parsed (search/parse-search-params {})
          result (search/validate-search-params parsed)]
      (is (contains? result :params))
      (is (not (contains? result :errors))))))

(deftest validate-page-zero-fails-test
  (testing "page=0 fails validation"
    (let [parsed (search/parse-search-params {"page" "0"})
          result (search/validate-search-params parsed)]
      (is (contains? result :errors))
      (is (some #(= "page" (:field %)) (:errors result))))))

(deftest validate-page-negative-fails-test
  (testing "Negative page fails validation"
    (let [parsed (search/parse-search-params {"page" "-1"})
          result (search/validate-search-params parsed)]
      (is (contains? result :errors))
      (is (some #(= "page" (:field %)) (:errors result))))))

(deftest validate-page-non-numeric-fails-test
  (testing "Non-numeric page fails validation"
    (let [parsed (search/parse-search-params {"page" "abc"})
          result (search/validate-search-params parsed)]
      (is (contains? result :errors))
      (is (some #(= "page" (:field %)) (:errors result))))))

(deftest validate-per-page-over-max-fails-test
  (testing "perPage > 100 fails validation"
    (let [parsed (search/parse-search-params {"perPage" "200"})
          result (search/validate-search-params parsed)]
      (is (contains? result :errors))
      (is (some #(= "perPage" (:field %)) (:errors result))))))

(deftest validate-per-page-zero-fails-test
  (testing "perPage=0 fails validation"
    (let [parsed (search/parse-search-params {"perPage" "0"})
          result (search/validate-search-params parsed)]
      (is (contains? result :errors))
      (is (some #(= "perPage" (:field %)) (:errors result))))))

(deftest validate-price-min-non-numeric-fails-test
  (testing "Non-numeric priceMin fails validation"
    (let [parsed (search/parse-search-params {"priceMin" "abc"})
          result (search/validate-search-params parsed)]
      (is (contains? result :errors))
      (is (some #(= "priceMin" (:field %)) (:errors result))))))

(deftest validate-price-max-non-numeric-fails-test
  (testing "Non-numeric priceMax fails validation"
    (let [parsed (search/parse-search-params {"priceMax" "xyz"})
          result (search/validate-search-params parsed)]
      (is (contains? result :errors))
      (is (some #(= "priceMax" (:field %)) (:errors result))))))

(deftest validate-price-min-greater-than-max-fails-test
  (testing "priceMin > priceMax fails validation"
    (let [parsed (search/parse-search-params {"priceMin" "100"
                                              "priceMax" "50"})
          result (search/validate-search-params parsed)]
      (is (contains? result :errors))
      (is (some #(= "priceMin" (:field %)) (:errors result))))))

(deftest validate-invalid-sort-by-fails-test
  (testing "Invalid sortBy fails validation"
    (let [parsed (search/parse-search-params {"sortBy" "invalid"})
          result (search/validate-search-params parsed)]
      (is (contains? result :errors))
      (is (some #(= "sortBy" (:field %)) (:errors result))))))

(deftest validate-invalid-sort-order-fails-test
  (testing "Invalid sortOrder fails validation"
    (let [parsed (search/parse-search-params {"sortOrder" "sideways"})
          result (search/validate-search-params parsed)]
      (is (contains? result :errors))
      (is (some #(= "sortOrder" (:field %)) (:errors result))))))

(deftest validate-multiple-errors-test
  (testing "Multiple invalid params return multiple errors"
    (let [parsed (search/parse-search-params {"page" "abc"
                                              "perPage" "200"
                                              "priceMin" "xyz"})
          result (search/validate-search-params parsed)
          fields (set (map :field (:errors result)))]
      (is (contains? result :errors))
      (is (contains? fields "page"))
      (is (contains? fields "perPage"))
      (is (contains? fields "priceMin")))))

;; ============================================================
;; build-search-query
;; ============================================================

(deftest build-query-default-test
  (testing "Default query: select all, order by name ASC, limit 20 offset 0"
    (let [params {:page 1 :per-page 20}
          query (search/build-search-query params)
          [sql & _sql-params] (hsql/format query)]
      (is (str/includes? sql "SELECT"))
      (is (str/includes? sql "FROM products"))
      (is (str/includes? sql "ORDER BY name ASC"))
      (is (str/includes? sql "LIMIT"))
      (is (str/includes? sql "OFFSET")))))

(deftest build-query-with-text-search-test
  (testing "Query with q adds prefix tsvector WHERE and ts_rank ORDER BY"
    (let [params {:page 1 :per-page 20 :q "running shoes"}
          query (search/build-search-query params)
          [sql & sql-params] (hsql/format query)]
      (is (str/includes? sql "search_vector @@ to_tsquery"))
      (is (str/includes? sql "ts_rank"))
      (is (some #(= "running:* & shoes:*" %) sql-params)
          "Search term should be converted to prefix tsquery"))))

(deftest build-query-prefix-matching-test
  (testing "Single partial word produces a prefix tsquery with :*"
    (let [params {:page 1 :per-page 20 :q "mon"}
          [sql & sql-params] (hsql/format (search/build-search-query params))]
      (is (str/includes? sql "to_tsquery"))
      (is (some #(= "mon:*" %) sql-params)
          "Partial keyword must become prefix query"))))

(deftest build-query-tsquery-operators-stripped-test
  (testing "tsquery special characters are stripped from user input"
    (let [params {:page 1 :per-page 20 :q "shoes & boots | (sandals)"}
          [_ & sql-params] (hsql/format (search/build-search-query params))]
      (is (some #(= "shoes:* & boots:* & sandals:*" %) sql-params)
          "Operators must be stripped, words get :* suffix"))))

(deftest build-query-q-with-sort-by-overrides-relevance-test
  (testing "When q and sortBy are both present, sortBy wins over ts_rank"
    (let [params {:page 1 :per-page 20 :q "running" :sort-by "price"
                  :sort-order "desc"}
          query (search/build-search-query params)
          [sql & _] (hsql/format query)]
      (is (str/includes? sql "search_vector @@ to_tsquery"))
      (is (str/includes? sql "ORDER BY price DESC"))
      (is (not (str/includes? sql "ts_rank"))))))

(deftest build-query-category-filter-test
  (testing "Category filter adds WHERE category = ?"
    (let [params {:page 1 :per-page 20 :category "Footwear"}
          query (search/build-search-query params)
          [sql & sql-params] (hsql/format query)]
      (is (str/includes? sql "category = ?"))
      (is (= "Footwear" (first sql-params))))))

(deftest build-query-price-range-test
  (testing "Price range adds WHERE price >= ? AND price <= ?"
    (let [params {:page 1 :per-page 20
                  :price-min (BigDecimal. "10")
                  :price-max (BigDecimal. "100")}
          query (search/build-search-query params)
          [sql & sql-params] (hsql/format query)]
      (is (str/includes? sql "price >= ?"))
      (is (str/includes? sql "price <= ?"))
      ;; Price values should be among bound parameters
      (is (some #(= (BigDecimal. "10") %) sql-params))
      (is (some #(= (BigDecimal. "100") %) sql-params)))))

(deftest build-query-pagination-offset-test
  (testing "Page 3 with perPage 10 produces OFFSET 20"
    (let [params {:page 3 :per-page 10}
          query (search/build-search-query params)]
      (is (= 10 (:limit query)))
      (is (= 20 (:offset query))))))

(deftest build-query-cumulative-filters-test
  (testing "Multiple filters combine with AND"
    (let [params {:page 1 :per-page 20
                  :q "shoe" :category "Footwear"
                  :price-min (BigDecimal. "50")
                  :price-max (BigDecimal. "200")}
          query (search/build-search-query params)
          [sql & sql-params] (hsql/format query)]
      (is (str/includes? sql "search_vector @@ to_tsquery"))
      (is (str/includes? sql "category = ?"))
      (is (str/includes? sql "price >= ?"))
      (is (str/includes? sql "price <= ?"))
      (is (str/includes? sql "AND"))
      ;; q appears twice (where + order-by ts_rank)
      (is (>= (count sql-params) 4)))))

(deftest build-query-sort-name-asc-test
  (testing "sortBy=name sortOrder=asc"
    (let [params {:page 1 :per-page 20 :sort-by "name" :sort-order "asc"}
          [sql & _] (hsql/format (search/build-search-query params))]
      (is (str/includes? sql "ORDER BY name ASC")))))

(deftest build-query-sort-price-desc-test
  (testing "sortBy=price sortOrder=desc"
    (let [params {:page 1 :per-page 20 :sort-by "price" :sort-order "desc"}
          [sql & _] (hsql/format (search/build-search-query params))]
      (is (str/includes? sql "ORDER BY price DESC")))))

(deftest build-query-sort-stock-test
  (testing "sortBy=stock sortOrder=asc"
    (let [params {:page 1 :per-page 20 :sort-by "stock" :sort-order "asc"}
          [sql & _] (hsql/format (search/build-search-query params))]
      (is (str/includes? sql "ORDER BY stock ASC")))))

;; ============================================================
;; build-count-query
;; ============================================================

(deftest build-count-default-test
  (testing "Count query with no filters"
    (let [params {:page 1 :per-page 20}
          query (search/build-count-query params)
          [sql & _] (hsql/format query)]
      (is (str/includes? sql "count(*)"))
      (is (str/includes? sql "FROM products"))
      (is (not (str/includes? sql "ORDER BY")))
      (is (not (str/includes? sql "LIMIT"))))))

(deftest build-count-with-filters-test
  (testing "Count query inherits the same WHERE as search query"
    (let [params {:page 1 :per-page 20
                  :category "Footwear"
                  :price-min (BigDecimal. "10")}
          [sql & sql-params] (hsql/format (search/build-count-query params))]
      (is (str/includes? sql "category = ?"))
      (is (str/includes? sql "price >= ?"))
      (is (= 2 (count sql-params))))))

;; ============================================================
;; build-paging-urls
;; ============================================================

(deftest paging-first-page-test
  (testing "First page: prev is nil, next points to page 2"
    (let [params {:page 1 :per-page 20}
          result (search/build-paging-urls "/api/products" params 50)]
      (is (nil? (:prev result)))
      (is (some? (:next result)))
      (is (str/includes? (:next result) "page=2"))
      (is (str/includes? (:next result) "perPage=20")))))

(deftest paging-middle-page-test
  (testing "Middle page: both prev and next present"
    (let [params {:page 2 :per-page 20}
          result (search/build-paging-urls "/api/products" params 50)]
      (is (str/includes? (:prev result) "page=1"))
      (is (str/includes? (:next result) "page=3")))))

(deftest paging-last-page-test
  (testing "Last page: prev present, next is nil"
    (let [params {:page 3 :per-page 20}
          result (search/build-paging-urls "/api/products" params 50)]
      (is (some? (:prev result)))
      (is (nil? (:next result))))))

(deftest paging-single-page-test
  (testing "Single page: both prev and next are nil"
    (let [params {:page 1 :per-page 20}
          result (search/build-paging-urls "/api/products" params 15)]
      (is (nil? (:prev result)))
      (is (nil? (:next result))))))

(deftest paging-empty-results-test
  (testing "Empty results: both prev and next are nil"
    (let [params {:page 1 :per-page 20}
          result (search/build-paging-urls "/api/products" params 0)]
      (is (nil? (:prev result)))
      (is (nil? (:next result))))))

(deftest paging-beyond-last-page-test
  (testing "Page beyond last: prev present, next is nil"
    (let [params {:page 5 :per-page 20}
          result (search/build-paging-urls "/api/products" params 50)]
      (is (some? (:prev result)))
      (is (str/includes? (:prev result) "page=4"))
      (is (nil? (:next result))))))

(deftest paging-preserves-all-params-test
  (testing "Paging URLs preserve all query params"
    (let [params {:page 1 :per-page 10
                  :q "running" :category "Footwear"
                  :price-min (BigDecimal. "10")
                  :price-max (BigDecimal. "100")
                  :sort-by "price" :sort-order "desc"}
          result (search/build-paging-urls "/api/products" params 50)
          next-url (:next result)]
      (is (str/includes? next-url "page=2"))
      (is (str/includes? next-url "perPage=10"))
      (is (str/includes? next-url "q=running"))
      (is (str/includes? next-url "category=Footwear"))
      (is (str/includes? next-url "priceMin=10"))
      (is (str/includes? next-url "priceMax=100"))
      (is (str/includes? next-url "sortBy=price"))
      (is (str/includes? next-url "sortOrder=desc")))))

(deftest paging-url-encodes-special-chars-test
  (testing "Special characters in q and category are URL-encoded"
    (let [params {:page 1 :per-page 20 :q "shoes & boots"
                  :category "Men's Wear"}
          result (search/build-paging-urls "/api/products" params 50)
          next-url (:next result)]
      (is (not (str/includes? next-url "shoes & boots")))
      (is (str/includes? next-url "shoes"))
      (is (not (str/includes? next-url "Men's Wear"))))))

;; ============================================================
;; Security: parameterization
;; ============================================================

(deftest xss-in-query-parameterized-test
  (testing "XSS content in q is sanitized and parameterized, not inlined in SQL"
    (let [xss-payload "<script>alert(1)</script>"
          params {:page 1 :per-page 20 :q xss-payload}
          [sql & sql-params] (hsql/format (search/build-search-query params))]
      (is (not (str/includes? sql "<script>"))
          "XSS payload must not be inlined in SQL")
      (is (not (str/includes? sql "alert"))
          "XSS function name must not be inlined in SQL")
      (is (seq sql-params)
          "Sanitized query must be a bound parameter"))))

(deftest sqli-in-query-parameterized-test
  (testing "SQL injection content in q is parameterized, never interpolated in SQL"
    (let [sqli-payload "'; DROP TABLE products;--"
          params {:page 1 :per-page 20 :q sqli-payload}
          [sql & sql-params] (hsql/format (search/build-search-query params))]
      (is (not (str/includes? sql "DROP TABLE"))
          "SQL injection must not be inlined in SQL")
      (is (not (str/includes? sql sqli-payload))
          "Raw payload must not appear in SQL string")
      (is (seq sql-params)
          "Query value must be a bound parameter"))))

(deftest sqli-in-category-parameterized-test
  (testing "SQL injection in category is parameterized"
    (let [sqli-payload "'; DROP TABLE products;--"
          params {:page 1 :per-page 20 :category sqli-payload}
          [sql & sql-params] (hsql/format (search/build-search-query params))]
      (is (not (str/includes? sql "DROP TABLE"))
          "SQL injection must not be inlined in SQL")
      (is (some #(= sqli-payload %) sql-params)
          "SQL injection payload must be a bound parameter"))))
