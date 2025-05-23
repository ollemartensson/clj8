(ns clj8.macros.macros
  (:require [clojure.string :as str]
            [clj8.registry.registry :as reg]
            [clj8.client.client :as client]))

(defn- format-param-for-doc [param-def]
  (let [schema (get param-def "schema")
        type-info (cond
                    (get schema "type") (get schema "type")
                    (get schema "$ref") (str "ref: " (get schema "$ref"))
                    :else "any")]
    (str "    - :" (get param-def "name") " (" (get param-def "in")
         (if (get param-def "required") ", required" ", optional")
         ", type: " type-info ")"
         (when-let [desc (get param-def "description")]
           (str "\n      " (str/replace desc #"\n" "\n      "))))))

(defn- generate-fn-name [op-keyword op-details]
  (if-let [op-id (:operation-id op-details)]
    (-> op-id
        (str/replace #"([a-z0-9])([A-Z])" "$1-$2") ; camelCase to kebab-case
        (str/replace #"([A-Z]+)([A-Z][a-z])" "$1-$2") ; Handle acronyms like "CoreV1" -> "core-v1"
        (str/lower-case)
        (symbol))
    (symbol (name op-keyword)))) ; Fallback to the keyword name itself

(defn- get-body-param-schema-ref [op-details]
  (some->> (:parameters op-details)
           (filter #(= "body" (get % "in")))
           first
           (get "schema")))

(defn- get-success-response-schema-ref [op-details]
  (let [responses (:responses op-details)
        ;; Prefer 200, then 201, then 204 as success
        success-resp (or (get responses "200")
                         (get responses "201")
                         (get responses "204")
                         (first (vals responses)))] ; Fallback if specific codes not found
    (or (get-in success-resp ["content" "application/json" "schema"])
        (get success-resp "schema"))))

(defmacro defk8sapi-fns
  "Defines functions for all Kubernetes API operations found in the registry.
  Each function will call `clj8.client.client/invoke` with the appropriate
  operation keyword and parameters. Functions will have docstrings and metadata
  derived from the OpenAPI specification."
  []
  (let [registry (reg/get-registry)
        forms (map (fn [[op-keyword op-details]]
                     (let [fn-name (generate-fn-name op-keyword op-details)
                           docstring (str (:summary op-details "")
                                          (when-let [desc (:description op-details)]
                                            (when (not= desc (:summary op-details))
                                              (str "\n\n" (str/replace desc #"\n" "\n"))))
                                          "\n\nParameters (passed as a map):\n"
                                          (str/join "\n" (map format-param-for-doc (:parameters op-details)))
                                          (when-let [body-schema (get-body-param-schema-ref op-details)]
                                            (str "\n\nBody schema: "
                                                 (or (get body-schema "$ref") (get body-schema "type") "object"))))
                           metadata {:doc docstring
                                     :arglists ''([params])
                                     :openapi/operation-id (:operation-id op-details)
                                     :openapi/method (:method op-details)
                                     :openapi/path (:path op-details)
                                     :malli/request-body-schema-ref (get-body-param-schema-ref op-details)
                                     :malli/response-schema-ref (get-success-response-schema-ref op-details)}]
                       `(~'defn ~fn-name
                                ~metadata
                                [~'params]
                                (client/invoke '~op-keyword ~'params))))
                   registry)]
    `(do ~@forms)))

(comment
  ;; To use this, you would typically have a namespace like `clj8.api`
  ;; and in that namespace, you would call:
  ;; (require '[clj8.macros.macros :refer [defk8sapi-fns]])
  ;; (defk8sapi-fns)
  ;;
  ;; This will define all the API functions in the `clj8.api` namespace.

  ;; Example of what a generated function's metadata might look like (simplified):
  (let [op-keyword :io.k8s.api.core.v1.createNamespacedPod
        op-details (get (reg/get-registry) op-keyword)]
    (when op-details
      {:fn-name (generate-fn-name op-keyword op-details)
       :doc (str (:summary op-details) "\n\nParameters:\n"
                 (str/join "\n" (map format-param-for-doc (:parameters op-details))))
       :arglists '([params])
       :openapi/operation-id (:operation-id op-details)
       :malli/request-body-schema-ref (get-body-param-schema-ref op-details)
       :malli/response-schema-ref (get-success-response-schema-ref op-details)})))