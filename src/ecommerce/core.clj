(ns ecommerce.core
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [ecommerce.config :as config]
            [ecommerce.db :as db]
            [ecommerce.import.worker :as import-worker]
            [ecommerce.router :as router])
  (:gen-class))

(defn -main
  "Application entry point.
   Loads config, initializes DB pool, starts import worker, starts Jetty,
   registers shutdown hook."
  [& _args]
  (let [cfg            (config/load-config)
        datasource     (db/create-datasource cfg)
        import-channel (async/chan 10)
        _              (import-worker/start-worker datasource import-channel)
        cookie-secret  (:cookie-secret cfg)
        handler        (router/create-router datasource import-channel cookie-secret)
        port           (get-in cfg [:server :port])
        server         (jetty/run-jetty handler
                                        {:port  port
                                         :join? false})]

    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread.
      ^Runnable
      (fn []
        (log/info "Shutting down...")
        (try (async/close! import-channel) (catch Exception e (log/warn e "Error closing import channel")))
        (try (.stop server)   (catch Exception e (log/warn e "Error stopping Jetty")))
        (try (db/close-datasource datasource) (catch Exception e (log/warn e "Error closing datasource")))
        (log/info "Shutdown complete"))))

    (log/info (str "Server started on port " port))))
