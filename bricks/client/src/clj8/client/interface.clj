(ns clj8.client.interface
  "Interface for clj8 HTTP client operations."
  (:require [clj8.client.client :as impl]))

;; Re-export client functions
(def invoke impl/invoke)
(def connect! impl/connect!)
(def current-connection impl/current-connection)
(def with-connection impl/with-connection)
(def create-default-middleware impl/create-default-middleware)