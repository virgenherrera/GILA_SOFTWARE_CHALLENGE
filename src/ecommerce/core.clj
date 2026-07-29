(ns ecommerce.core
  (:require [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [ecommerce.config :as config]
            [ecommerce.db :as db]
            [ecommerce.router :as router])
  (:gen-class))

(defn -main
  "Application entry point.
   Loads config, initializes DB pool, starts Jetty, registers shutdown hook."
  [& _args]
  (let [cfg        (config/load-config)
        datasource (db/create-datasource cfg)
        handler    (router/create-router datasource)
        port       (get-in cfg [:server :port])
        server     (jetty/run-jetty handler
                                    {:port  port
                                     :join? false})]

    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread.
      ^Runnable
      (fn []
        (log/info "Shutting down...")
        (try (.stop server)   (catch Exception e (log/warn e "Error stopping Jetty")))
        (try (db/close-datasource datasource) (catch Exception e (log/warn e "Error closing datasource")))
        (log/info "Shutdown complete"))))

    (log/info (str "Server started on port " port))))
