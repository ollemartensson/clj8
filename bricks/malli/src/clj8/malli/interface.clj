(ns clj8.malli.interface
  "Interface for clj8 Malli schema operations."
  (:require [clj8.malli.malli :as impl]))

;; Re-export malli functions
(def extract-schemas impl/extract-schemas)
(def get-schema impl/get-schema)
(def validate-with-schema impl/validate-with-schema)
(def generate-sample impl/generate-sample)
(def openapi->malli-registry impl/openapi->malli-registry)