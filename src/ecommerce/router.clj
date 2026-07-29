(ns ecommerce.router
  (:require [reitit.ring :as ring]
            [reitit.coercion.malli]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [next.jdbc :as jdbc]
            [clojure.data.json :as json]
            [ecommerce.middleware :as mw]
            [ecommerce.product.handler :as product-handler]))

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
  "Create the reitit ring router with all routes and middleware."
  [datasource]
  (ring/ring-handler
   (ring/router
    [["/api"
      ["/health" {:get {:summary "Health check"
                        :handler (health-handler datasource)}}]
      ["/products" {:post {:summary "Create a product"
                           :handler (product-handler/create-product datasource)}}]
      ["/swagger.json" {:get {:no-doc true
                              :swagger {:info {:title "E-Commerce API"
                                               :description "E-Commerce backend API"
                                               :version "1.0.0"}}
                              :handler (swagger/create-swagger-handler)}}]]]
    {:data {:coercion reitit.coercion.malli/coercion
            :middleware [mw/wrap-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/api/docs"
      :url "/api/swagger.json"})
    (ring/create-default-handler
     {:not-found (constantly {:status 404
                              :headers {"Content-Type" "application/json"}
                              :body (json/write-str {:error {:code "NOT_FOUND"
                                                             :message "Resource not found"}})})}))))
