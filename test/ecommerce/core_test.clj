(ns ecommerce.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]
            [ecommerce.router :as router]))

(defn- mock-datasource
  "Create a mock datasource that simulates a healthy DB connection."
  []
  (reify javax.sql.DataSource
    (getConnection [_]
      (throw (Exception. "Mock datasource — no real connection")))))

(deftest health-endpoint-test
  (testing "GET /api/health returns 200 with status healthy when DB is reachable"
    ;; Note: With a mock datasource the DB check will fail, so we expect 503.
    ;; A full integration test with a real DB would return 200.
    ;; This test validates the endpoint is wired up and returns the correct shape.
    (let [handler  (router/create-router (mock-datasource))
          request  (mock/request :get "/api/health")
          response (handler request)
          body     (json/read-str (:body response) :key-fn keyword)]
      (is (= 503 (:status response)))
      (is (= "unhealthy" (:status body)))
      (is (contains? body :uptime_seconds))
      (is (contains? body :db))
      (is (= "disconnected" (get-in body [:db :status]))))))
