(ns clj8.core.interface
  "Interface for clj8 core utilities."
  (:require [clj8.core.core :as impl]))

;; Re-export core functions
(def version impl/version)
(def health-check impl/health-check)
(def configure impl/configure)