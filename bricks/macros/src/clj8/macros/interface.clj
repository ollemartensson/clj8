(ns clj8.macros.interface
  "Interface for clj8 macro generation."
  (:require [clj8.macros.macros :as impl]))

;; Re-export macro functions and macros
(def generate-fn-name impl/generate-fn-name)
(def generate-function-metadata impl/generate-function-metadata)
(defmacro defk8sapi-fn [& args] `(impl/defk8sapi-fn ~@args))
(defmacro defk8sapi-fns [& args] `(impl/defk8sapi-fns ~@args))