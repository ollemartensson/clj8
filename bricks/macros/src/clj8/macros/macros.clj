(ns clj8.macros.macros
  "Generates ergonomic, documented functions from the Kubernetes API registry."
  (:require [clojure.string :as str]
            [clj8.registry.registry :as reg]
            [clj8.client.client :as client]
            [clj8.malli.malli :as malli]))

(defn generate-fn-name
  "Generate a function name from operation metadata.
  
  Args:
    op-meta: Operation metadata map
    
  Returns:
    Function name symbol"
  [op-meta]
  (let [op-id (:operation-id op-meta)]
    (if (and op-id (not (str/blank? op-id)))
      (-> op-id
          (str/replace #"([a-z])([A-Z])" "$1-$2")
          (str/lower-case)
          (str/replace #"[^a-zA-Z0-9\-]" "-")
          (str/replace #"-+" "-")
          (str/replace #"^-|-$" "")
          symbol)
      (-> (:path op-meta)
          (str/replace #"/" "-")
          (str/replace #"[{}]" "")
          (str/replace #"[^a-zA-Z0-9\-]" "-")
          (str/replace #"-+" "-")
          (str/replace #"^-|-$" "")
          symbol))))

(defn format-param-for-doc
  "Format a parameter definition for documentation.
  
  Args:
    param: Parameter definition map
    
  Returns:
    Formatted documentation string"
  [param]
  (let [name (or (get param "name") (get param :name))
        in (or (get param "in") (get param :in))
        required (or (get param "required") (get param :required))
        schema (or (get param "schema") (get param :schema))
        type (or (get schema "type") (get schema :type))
        description (or (get param "description") (get param :description))]
    (str "  - " name " (" in ")"
         (when required " [required]")
         (when type (str " : " type))
         (when description (str " - " description)))))

(defn get-body-param-schema-ref
  "Extract the schema reference for body parameters.
  
  Args:
    op-meta: Operation metadata
    
  Returns:
    Schema reference string or nil"
  [op-meta]
  (let [schemas (:schemas op-meta)
        request-schema (:request schemas)]
    (or (get request-schema "$ref")
        (get request-schema :$ref))))

(defn get-success-response-schema-ref
  "Extract the schema reference for success responses.
  
  Args:
    op-meta: Operation metadata
    
  Returns:
    Schema reference string or nil"
  [op-meta]
  (let [schemas (:schemas op-meta)
        response-schema (:response schemas)]
    (or (get response-schema "$ref")
        (get response-schema :$ref))))

(defn extract-schema-name
  "Extract schema name from $ref string.
  
  Args:
    ref-str: Schema reference like '#/components/schemas/Pod'
    
  Returns:
    Schema name keyword like :io.k8s.api.core.v1.Pod"
  [ref-str]
  (when ref-str
    (-> ref-str
        (str/replace "#/components/schemas/" "")
        keyword)))

(defn generate-function-metadata
  "Generate rich metadata for a K8s API function.
  
  Args:
    op-meta: Operation metadata from registry
    spec: Full OpenAPI specification
    
  Returns:
    Metadata map with docs, schemas, arglists"
  [op-meta spec]
  (let [fn-name (generate-fn-name op-meta)
        summary (:summary op-meta)
        method (:method op-meta)
        path (:path op-meta)
        parameters (:parameters op-meta)
        request-schema-ref (get-body-param-schema-ref op-meta)
        response-schema-ref (get-success-response-schema-ref op-meta)
        request-schema-name (extract-schema-name request-schema-ref)
        response-schema-name (extract-schema-name response-schema-ref)]

    {:doc (str summary "\n\n"
               "Method: " (str/upper-case (name method)) "\n"
               "Path: " path "\n"
               (when (seq parameters)
                 (str "\nParameters:\n"
                      (str/join "\n" (map format-param-for-doc parameters))))
               (when request-schema-name
                 (str "\nRequest schema: " request-schema-name))
               (when response-schema-name
                 (str "\nResponse schema: " response-schema-name)))

     :arglists '([params] [params options])

     :clj8/operation-id (:operation-id op-meta)
     :clj8/method method
     :clj8/path path
     :clj8/request-schema request-schema-name
     :clj8/response-schema response-schema-name
     :clj8/parameters parameters}))

(defn generate-function-with-validation
  "Generate a K8s API function with Malli validation.
  
  Args:
    fn-name: Function name symbol
    op-keyword: Operation keyword for registry lookup
    metadata: Rich metadata map
    
  Returns:
    Function definition form"
  [fn-name op-keyword metadata]
  `(defn ~fn-name
     ~metadata
     ([params#]
      (~fn-name params# {}))
     ([params# options#]
      (let [op-meta# (reg/lookup ~op-keyword)]
        (when-not op-meta#
          (throw (ex-info (str "Operation not found: " ~op-keyword)
                          {:operation ~op-keyword})))

        ;; TODO: Add Malli validation for request params
        ;; (when-let [request-schema# (:clj8/request-schema (meta (var ~fn-name)))]
        ;;   (malli/validate-request params# request-schema#))

        (let [result# (client/invoke ~op-keyword (merge params# options#))]
          ;; TODO: Add Malli validation for response
          ;; (when-let [response-schema# (:clj8/response-schema (meta (var ~fn-name)))]
          ;;   (malli/validate-response result# response-schema#))
          result#)))))

(defn generate-operation-function
  "Generate a single K8s API function from operation metadata.
  
  Args:
    op-keyword: Operation keyword
    op-meta: Operation metadata
    spec: Full OpenAPI specification
    
  Returns:
    Function definition form"
  [op-keyword op-meta spec]
  (let [fn-name (generate-fn-name op-meta)
        metadata (generate-function-metadata op-meta spec)]
    (generate-function-with-validation fn-name op-keyword metadata)))

(defmacro defk8sapi-fns
  "Generate all Kubernetes API functions from the registry.
  
  This macro creates a function for each operation in the K8s API registry,
  with rich metadata, documentation, and schema information.
  
  Usage:
    (defk8sapi-fns)
    
  This will generate functions like:
    (list-namespaced-pod {:namespace \"default\"})
    (create-namespaced-service {...})
    etc."
  []
  (let [registry (reg/get-registry)
        spec (reg/load-openapi-spec)]
    `(do
       ~@(for [[op-keyword op-meta] registry]
           (generate-operation-function op-keyword op-meta spec)))))

(defmacro defk8sapi-fn
  "Generate a single Kubernetes API function.
  
  Args:
    op-keyword: Operation keyword to generate function for
    
  Usage:
    (defk8sapi-fn :listNamespacedPod)
    
  This creates a function with rich metadata and validation."
  [op-keyword]
  (let [registry (reg/get-registry)
        op-meta (get registry op-keyword)
        spec (reg/load-openapi-spec)]
    (when-not op-meta
      (throw (ex-info (str "Operation not found in registry: " op-keyword)
                      {:operation op-keyword
                       :available-operations (keys registry)})))
    (generate-operation-function op-keyword op-meta spec)))