(ns ecommerce.router
  (:require [reitit.ring :as ring]
            [reitit.coercion.malli]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [next.jdbc :as jdbc]
            [clojure.data.json :as json]
            [ring.middleware.multipart-params :as multipart]
            [ecommerce.middleware :as mw]
            [ecommerce.product.handler :as product-handler]
            [ecommerce.import.handler :as import-handler]
            [ecommerce.cart.handler :as cart-handler]
            [ecommerce.cart.middleware :as cart-mw]
            [ecommerce.checkout.handler :as checkout-handler]))

(def ^:private start-time (System/currentTimeMillis))

(defn- health-handler
  "Health check endpoint. Returns DB connectivity status and uptime."
  [datasource]
  (fn [_request]
    (let [uptime-seconds (quot (- (System/currentTimeMillis) start-time) 1000)
          db-status (try
                      (let [start (System/nanoTime)
                            _     (jdbc/execute-one! datasource ["SELECT 1"])
                            elapsed-ms (/ (double (- (System/nanoTime) start)) 1e6)]
                        {:status "connected" :latency_ms (Math/round elapsed-ms)})
                      (catch Exception _
                        {:status "disconnected" :latency_ms -1}))
          healthy? (= "connected" (:status db-status))]
      {:status (if healthy? 200 503)
       :headers {"Content-Type" "application/json"}
       :body (json/write-str {:status (if healthy? "healthy" "unhealthy")
                              :uptime_seconds uptime-seconds
                              :db db-status})})))

(defn create-router
  "Create the reitit ring router with all routes and middleware.
   Accepts an optional import-channel for CSV import background processing
   and an optional cookie-secret for cart session management."
  ([datasource]
   (create-router datasource nil))
  ([datasource import-channel]
   (create-router datasource import-channel nil))
  ([datasource import-channel cookie-secret]
   (ring/ring-handler
    (ring/router
     [["/api"
       ["/health" {:get {:summary "Health check"
                         :handler (health-handler datasource)}}]
       ["/products" {:get {:summary "List products"
                           :handler (product-handler/list-products datasource)}
                     :post {:summary "Create a product"
                            :handler (product-handler/create-product datasource)}}]
       ["/products/categories" {:get {:summary "List product categories"
                                      :handler (product-handler/list-categories datasource)}}]
       ["/products/:sku" {:get {:summary "Get a product by SKU"
                                :handler (product-handler/get-product datasource)}
                          :put {:summary "Update a product"
                                :handler (product-handler/update-product datasource)}
                          :delete {:summary "Delete a product"
                                   :handler (product-handler/delete-product datasource)}}]
       ["/cart" {:middleware [(cart-mw/wrap-cart-cookie cookie-secret)]}
        ["" {:get {:summary "Get cart"
                   :handler (cart-handler/get-cart datasource)}}]
        ["/items" {:post {:summary "Add item to cart"
                          :handler (cart-handler/add-item datasource)}}]
        ["/items/:sku" {:put {:summary "Update item quantity"
                              :handler (cart-handler/update-item datasource)}
                        :delete {:summary "Remove item from cart"
                                 :handler (cart-handler/remove-item datasource)}}]]
       ["/checkout" {:middleware [(cart-mw/wrap-cart-cookie cookie-secret)]
                     :post {:summary "Checkout current cart"
                            :handler (checkout-handler/checkout datasource)}}]
       ["/orders/:id" {:get {:summary "Get order details"
                             :handler (checkout-handler/get-order datasource)}}]
       ["/imports" {:post {:summary "Upload CSV import"
                           :middleware [multipart/wrap-multipart-params]
                           :handler (import-handler/upload-csv datasource import-channel)}}]
       ["/imports/:id"
        ["" {:get {:summary "Get import job status"
                   :handler (import-handler/get-job-status datasource)}}]
        ["/errors" {:get {:summary "Get import errors"
                          :handler (import-handler/get-job-errors datasource)}}]]
       ["/swagger.json" {:get {:no-doc true
                               :swagger {:info {:title "E-Commerce API"
                                                :description "E-Commerce backend API"
                                                :version "1.0.0"}}
                               :handler (swagger/create-swagger-handler)}}]]]
     {:data {:coercion reitit.coercion.malli/coercion
             :middleware [mw/wrap-middleware]}
      :conflicts nil})
    (ring/routes
     (swagger-ui/create-swagger-ui-handler
      {:path "/api/docs"
       :url "/api/swagger.json"})
     (ring/create-default-handler
      {:not-found (constantly {:status 404
                               :headers {"Content-Type" "application/json"}
                               :body (json/write-str {:error {:code "NOT_FOUND"
                                                              :message "Resource not found"}})})})))))

