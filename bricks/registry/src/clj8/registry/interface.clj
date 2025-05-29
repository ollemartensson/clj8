(ns clj8.registry.interface
  "Interface for clj8 registry operations."
  (:require [clj8.registry.registry :as impl]))

;; Re-export registry functions
(def load-openapi-spec impl/load-openapi-spec)
(def generate-registry impl/generate-registry)
(def get-registry impl/get-registry)
(def lookup impl/lookup)
(def extract-endpoints impl/extract-endpoints)