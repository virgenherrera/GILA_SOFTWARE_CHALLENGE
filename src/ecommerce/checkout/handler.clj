(ns ecommerce.checkout.handler
  (:require [ecommerce.checkout.service :as service]
            [ecommerce.order.repository :as order-repo]
            [clojure.data.json :as json]))

(defn- json-value-fn
  "Custom value function for data.json to handle DB timestamp and numeric types."
  [_key value]
  (cond
    (instance? java.time.OffsetDateTime value) (str value)
    (instance? java.sql.Timestamp value) (str value)
    (instance? java.math.BigDecimal value) (double value)
    :else value))

(defn- error-response
  "Build a JSON error response with the given status, code, and message.
   Optionally includes extra fields merged into the error map."
  [status code message & {:as extra}]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body (json/write-str {:error (merge {:code code :message message} extra)})})

(defn checkout
  "POST /api/checkout handler. Performs atomic checkout of the current cart.
   Returns 201 with order details on success, 400/409 on error."
  [datasource]
  (fn [request]
    (let [cart-id (:cart-id request)]
      (try
        (let [order (service/perform-checkout! datasource cart-id)]
          {:status 201
           :headers {"Content-Type" "application/json"}
           :body (json/write-str order :value-fn json-value-fn)})
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (if (:items data)
              (error-response (:status data) (:code data) (:message data)
                              :items (:items data))
              (error-response (:status data) (:code data) (:message data)))))))))

(defn get-order
  "GET /api/orders/:id handler. Returns order details by UUID."
  [datasource]
  (fn [request]
    (let [id-str (-> request :path-params :id)]
      (try
        (let [order-id (java.util.UUID/fromString id-str)
              order (order-repo/find-order-by-id datasource order-id)]
          (if-not order
            (error-response 404 "NOT_FOUND" "Order not found")
            (let [items (order-repo/find-order-items datasource order-id)
                  response-items (mapv
                                  (fn [item]
                                    (let [qty (:quantity item)
                                          price (:price_at_purchase item)
                                          subtotal (* qty (bigdec price))]
                                      {:product_sku (:product_sku item)
                                       :name (:name item)
                                       :quantity qty
                                       :unit_price price
                                       :line_subtotal subtotal}))
                                  items)]
              {:status 200
               :headers {"Content-Type" "application/json"}
               :body (json/write-str {:id (str (:id order))
                                      :status (:status order)
                                      :placed_at (:created_at order)
                                      :items response-items
                                      :total_amount (:total order)}
                                     :value-fn json-value-fn)})))
        (catch IllegalArgumentException _
          (error-response 404 "NOT_FOUND" "Order not found"))))))
