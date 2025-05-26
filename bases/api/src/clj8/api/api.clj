(ns clj8.api.api
  (:require [clj8.macros.macros :refer [defk8sapi-fns]]))

;; This will define all Kubernetes API functions in this namespace.
(defk8sapi-fns)