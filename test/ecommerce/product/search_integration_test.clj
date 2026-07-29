(ns ecommerce.product.search-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [ring.mock.request :as mock]
            [ecommerce.router :as router]
            [ecommerce.product.repository :as repo]))

(defn- mock-datasource
  "Create a mock datasource (not connected to a real DB)."
  []
  (reify javax.sql.DataSource
    (getConnection [_]
      (throw (Exception. "Mock datasource - no real connection")))))

(defn- get-request
  "Create a GET request to the given path."
  [handler path]
  (handler (mock/request :get path)))

(defn- parse-body
  "Parse JSON response body to a Clojure map with keyword keys."
  [response]
  (json/read-str (:body response) :key-fn keyword))

;; --- Sample product data ---

(def ^:private sample-products
  [{:sku "RS-001" :name "Running Shoes" :description "Lightweight running shoes"
    :price 89.99 :category "Footwear" :stock 50
    :created_at "2024-01-01T00:00:00Z" :updated_at "2024-01-01T00:00:00Z"}
   {:sku "WB-002" :name "Water Bottle" :description "Stainless steel water bottle"
    :price 24.99 :category "Accessories" :stock 200
    :created_at "2024-01-02T00:00:00Z" :updated_at "2024-01-02T00:00:00Z"}
   {:sku "TS-003" :name "Training Shorts" :description "Breathable training shorts"
    :price 34.99 :category "Apparel" :stock 75
    :created_at "2024-01-03T00:00:00Z" :updated_at "2024-01-03T00:00:00Z"}])

;; ============================================================
;; GET /api/products — Default listing
;; ============================================================

(deftest list-products-default-test
  (testing "Default listing returns 200 with items and paging envelope"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/search-products (fn [_ds _params] sample-products)
                    repo/count-products (fn [_ds _params] 3)]
        (let [response (get-request handler "/api/products")
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (vector? (:items body)))
          (is (= 3 (count (:items body))))
          (is (= 1 (get-in body [:paging :page])))
          (is (= 20 (get-in body [:paging :perPage])))
          (is (= 3 (get-in body [:paging :total])))
          (is (nil? (get-in body [:paging :prev])))
          (is (nil? (get-in body [:paging :next]))))))))

;; ============================================================
;; GET /api/products — Pagination
;; ============================================================

(deftest list-products-pagination-test
  (testing "Pagination: page 2 of 3 shows prev and next"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/search-products (fn [_ds _params] (take 2 sample-products))
                    repo/count-products (fn [_ds _params] 50)]
        (let [response (get-request handler "/api/products?page=2&perPage=20")
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= 2 (get-in body [:paging :page])))
          (is (= 20 (get-in body [:paging :perPage])))
          (is (= 50 (get-in body [:paging :total])))
          (is (some? (get-in body [:paging :prev])))
          (is (str/includes? (get-in body [:paging :prev]) "page=1"))
          (is (some? (get-in body [:paging :next])))
          (is (str/includes? (get-in body [:paging :next]) "page=3")))))))

(deftest page-beyond-last-returns-empty-items-test
  (testing "Page beyond last returns 200 with empty items and correct paging"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/search-products (fn [_ds _params] [])
                    repo/count-products (fn [_ds _params] 50)]
        (let [response (get-request handler "/api/products?page=10&perPage=20")
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= [] (:items body)))
          (is (= 10 (get-in body [:paging :page])))
          (is (= 50 (get-in body [:paging :total])))
          (is (some? (get-in body [:paging :prev])))
          (is (nil? (get-in body [:paging :next]))))))))

(deftest empty-catalog-returns-200-test
  (testing "Empty catalog returns 200 with empty items and total=0"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/search-products (fn [_ds _params] [])
                    repo/count-products (fn [_ds _params] 0)]
        (let [response (get-request handler "/api/products")
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= [] (:items body)))
          (is (= 0 (get-in body [:paging :total])))
          (is (nil? (get-in body [:paging :prev])))
          (is (nil? (get-in body [:paging :next]))))))))

;; ============================================================
;; GET /api/products — Full-text search
;; ============================================================

(deftest search-with-q-param-test
  (testing "Search with q passes search term to repository"
    (let [handler (router/create-router (mock-datasource))
          captured-params (atom nil)]
      (with-redefs [repo/search-products (fn [_ds params]
                                           (reset! captured-params params)
                                           [(first sample-products)])
                    repo/count-products (fn [_ds _params] 1)]
        (let [response (get-request handler "/api/products?q=running")
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= 1 (count (:items body))))
          (is (= "running" (:q @captured-params))))))))

;; ============================================================
;; GET /api/products — Category filter
;; ============================================================

(deftest category-filter-test
  (testing "Category filter passes exact category to repository"
    (let [handler (router/create-router (mock-datasource))
          captured-params (atom nil)]
      (with-redefs [repo/search-products (fn [_ds params]
                                           (reset! captured-params params)
                                           [(first sample-products)])
                    repo/count-products (fn [_ds _params] 1)]
        (let [response (get-request handler
                                    "/api/products?category=Footwear")]
          (is (= 200 (:status response)))
          (is (= "Footwear" (:category @captured-params))))))))

;; ============================================================
;; GET /api/products — Price range filter
;; ============================================================

(deftest price-range-filter-test
  (testing "Price range filter passes priceMin/priceMax to repository"
    (let [handler (router/create-router (mock-datasource))
          captured-params (atom nil)]
      (with-redefs [repo/search-products (fn [_ds params]
                                           (reset! captured-params params)
                                           sample-products)
                    repo/count-products (fn [_ds _params] 3)]
        (let [response (get-request handler
                                    "/api/products?priceMin=20&priceMax=100")]
          (is (= 200 (:status response)))
          (is (= (BigDecimal. "20") (:price-min @captured-params)))
          (is (= (BigDecimal. "100") (:price-max @captured-params))))))))

;; ============================================================
;; GET /api/products — Sort controls
;; ============================================================

(deftest sort-by-price-desc-test
  (testing "Sort by price descending passes sortBy/sortOrder to repository"
    (let [handler (router/create-router (mock-datasource))
          captured-params (atom nil)]
      (with-redefs [repo/search-products (fn [_ds params]
                                           (reset! captured-params params)
                                           sample-products)
                    repo/count-products (fn [_ds _params] 3)]
        (let [response (get-request handler
                                    "/api/products?sortBy=price&sortOrder=desc")]
          (is (= 200 (:status response)))
          (is (= "price" (:sort-by @captured-params)))
          (is (= "desc" (:sort-order @captured-params))))))))

;; ============================================================
;; GET /api/products — Relevance ranking
;; ============================================================

(deftest relevance-ranking-q-without-sort-by-test
  (testing "q without sortBy: sort-by is nil (triggers ts_rank in query builder)"
    (let [handler (router/create-router (mock-datasource))
          captured-params (atom nil)]
      (with-redefs [repo/search-products (fn [_ds params]
                                           (reset! captured-params params)
                                           [(first sample-products)])
                    repo/count-products (fn [_ds _params] 1)]
        (get-request handler "/api/products?q=running")
        (is (= "running" (:q @captured-params)))
        (is (nil? (:sort-by @captured-params)))))))

(deftest relevance-q-with-sort-by-test
  (testing "q with sortBy: sortBy wins over relevance"
    (let [handler (router/create-router (mock-datasource))
          captured-params (atom nil)]
      (with-redefs [repo/search-products (fn [_ds params]
                                           (reset! captured-params params)
                                           [(first sample-products)])
                    repo/count-products (fn [_ds _params] 1)]
        (get-request handler "/api/products?q=running&sortBy=price")
        (is (= "running" (:q @captured-params)))
        (is (= "price" (:sort-by @captured-params)))))))

;; ============================================================
;; GET /api/products — Cumulative filters
;; ============================================================

(deftest cumulative-filters-test
  (testing "Multiple filters combine correctly"
    (let [handler (router/create-router (mock-datasource))
          captured-params (atom nil)]
      (with-redefs [repo/search-products
                    (fn [_ds params]
                      (reset! captured-params params)
                      [(first sample-products)])
                    repo/count-products (fn [_ds _params] 1)]
        (let [url (str "/api/products?q=shoe&category=Footwear"
                       "&priceMin=50&priceMax=200&sortBy=price"
                       "&sortOrder=desc")]
          (get-request handler url)
          (is (= "shoe" (:q @captured-params)))
          (is (= "Footwear" (:category @captured-params)))
          (is (= (BigDecimal. "50") (:price-min @captured-params)))
          (is (= (BigDecimal. "200") (:price-max @captured-params)))
          (is (= "price" (:sort-by @captured-params)))
          (is (= "desc" (:sort-order @captured-params))))))))

;; ============================================================
;; GET /api/products — Paging preserves params
;; ============================================================

(deftest paging-preserves-all-params-test
  (testing "Paging URLs include all query params"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/search-products (fn [_ds _params] sample-products)
                    repo/count-products (fn [_ds _params] 50)]
        (let [url (str "/api/products?page=2&perPage=10"
                       "&q=shoe&category=Footwear"
                       "&priceMin=10&priceMax=200"
                       "&sortBy=price&sortOrder=desc")
              response (get-request handler url)
              body (parse-body response)
              prev-url (get-in body [:paging :prev])
              next-url (get-in body [:paging :next])]
          ;; Prev URL
          (is (str/includes? prev-url "page=1"))
          (is (str/includes? prev-url "perPage=10"))
          (is (str/includes? prev-url "q=shoe"))
          (is (str/includes? prev-url "category=Footwear"))
          (is (str/includes? prev-url "priceMin=10"))
          (is (str/includes? prev-url "priceMax=200"))
          (is (str/includes? prev-url "sortBy=price"))
          (is (str/includes? prev-url "sortOrder=desc"))
          ;; Next URL
          (is (str/includes? next-url "page=3"))
          (is (str/includes? next-url "perPage=10")))))))

;; ============================================================
;; GET /api/products — Validation errors (400)
;; ============================================================

(deftest invalid-page-zero-returns-400-test
  (testing "page=0 returns 400 with VALIDATION_ERROR"
    (let [handler (router/create-router (mock-datasource))
          response (get-request handler "/api/products?page=0")
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code])))
      (is (some #(= "page" (:field %))
                (get-in body [:error :details]))))))

(deftest invalid-per-page-over-max-returns-400-test
  (testing "perPage=200 returns 400 with VALIDATION_ERROR"
    (let [handler (router/create-router (mock-datasource))
          response (get-request handler "/api/products?perPage=200")
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code])))
      (is (some #(= "perPage" (:field %))
                (get-in body [:error :details]))))))

(deftest invalid-price-min-non-numeric-returns-400-test
  (testing "priceMin=abc returns 400"
    (let [handler (router/create-router (mock-datasource))
          response (get-request handler "/api/products?priceMin=abc")
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code])))
      (is (some #(= "priceMin" (:field %))
                (get-in body [:error :details]))))))

(deftest price-min-greater-than-max-returns-400-test
  (testing "priceMin > priceMax returns 400 with field-level error"
    (let [handler (router/create-router (mock-datasource))
          response (get-request handler
                                "/api/products?priceMin=100&priceMax=50")
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "VALIDATION_ERROR" (get-in body [:error :code])))
      (is (some #(= "priceMin" (:field %))
                (get-in body [:error :details]))))))

;; ============================================================
;; GET /api/products — Security
;; ============================================================

(deftest xss-in-query-treated-as-text-test
  (testing "XSS payload in q is treated as plain search text"
    (let [handler (router/create-router (mock-datasource))
          captured-params (atom nil)]
      (with-redefs [repo/search-products (fn [_ds params]
                                           (reset! captured-params params)
                                           [])
                    repo/count-products (fn [_ds _params] 0)]
        (let [response (get-request handler
                                    (str "/api/products?q="
                                         "%3Cscript%3Ealert(1)%3C/script%3E"))]
          (is (= 200 (:status response)))
          (is (= "<script>alert(1)</script>" (:q @captured-params)))
          ;; Response body should not contain unescaped script tags
          ;; in error messages (there should be no error here)
          (is (not (str/includes? (str (:body response))
                                  "<script>alert"))))))))

(deftest sqli-in-query-neutralized-test
  (testing "SQL injection payload in q is treated as plain text"
    (let [handler (router/create-router (mock-datasource))
          captured-params (atom nil)]
      (with-redefs [repo/search-products (fn [_ds params]
                                           (reset! captured-params params)
                                           [])
                    repo/count-products (fn [_ds _params] 0)]
        (let [response (get-request handler
                                    (str "/api/products?q="
                                         "'+OR+1%3D1--"))]
          (is (= 200 (:status response)))
          ;; The payload should be passed as a bound parameter,
          ;; not interpolated into SQL
          (is (some? (:q @captured-params))))))))

(deftest no-internal-details-in-error-test
  (testing "Validation errors do not leak internal details"
    (let [handler (router/create-router (mock-datasource))
          response (get-request handler "/api/products?page=abc")
          body-str (:body response)]
      (is (= 400 (:status response)))
      ;; Should not contain stack traces or internal class names
      (is (not (str/includes? body-str "Exception")))
      (is (not (str/includes? body-str "clojure.lang")))
      (is (not (str/includes? body-str "java.lang"))))))

;; ============================================================
;; GET /api/products/:sku — Single product
;; ============================================================

(deftest get-product-found-test
  (testing "Existing SKU returns 200 with product"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/find-by-sku
                    (fn [_ds sku]
                      (when (= "RS-001" sku)
                        (first sample-products)))]
        (let [response (get-request handler "/api/products/RS-001")
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= "RS-001" (:sku body)))
          (is (= "Running Shoes" (:name body)))
          (is (= 89.99 (:price body))))))))

(deftest get-product-not-found-test
  (testing "Non-existent SKU returns 404 with NOT_FOUND"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/find-by-sku (fn [_ds _sku] nil)]
        (let [response (get-request handler "/api/products/NONEXISTENT")
              body (parse-body response)]
          (is (= 404 (:status response)))
          (is (= "NOT_FOUND" (get-in body [:error :code])))
          (is (= "Product not found" (get-in body [:error :message]))))))))

;; ============================================================
;; GET /api/products/categories
;; ============================================================

(deftest list-categories-test
  (testing "Categories endpoint returns sorted list"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/list-categories
                    (fn [_ds]
                      ["Accessories" "Apparel" "Footwear"])]
        (let [response (get-request handler "/api/products/categories")
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= ["Accessories" "Apparel" "Footwear"]
                 (:categories body))))))))

(deftest list-categories-empty-test
  (testing "Empty catalog returns empty categories list"
    (let [handler (router/create-router (mock-datasource))]
      (with-redefs [repo/list-categories (fn [_ds] [])]
        (let [response (get-request handler "/api/products/categories")
              body (parse-body response)]
          (is (= 200 (:status response)))
          (is (= [] (:categories body))))))))

;; ============================================================
;; Route resolution: /categories vs /:sku
;; ============================================================

(deftest categories-route-not-matched-as-sku-test
  (testing "/products/categories matches the categories route, not :sku"
    (let [handler (router/create-router (mock-datasource))
          sku-accessed (atom false)]
      (with-redefs [repo/list-categories (fn [_ds] ["Test"])
                    repo/find-by-sku (fn [_ds _sku]
                                       (reset! sku-accessed true)
                                       nil)]
        (let [response (get-request handler "/api/products/categories")]
          (is (= 200 (:status response)))
          (is (false? @sku-accessed)
              "categories route should not trigger find-by-sku"))))))
