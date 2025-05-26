(ns clj8.registry.registry
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def openapi-path "resources/k8s-openapi.json")

(defn load-openapi-spec
  "Loads and parses the Kubernetes OpenAPI spec as EDN (from JSON)."
  ([] (load-openapi-spec openapi-path))
  ([path]
   (println (str "Attempting to load OpenAPI spec from: " path " (resolved: " (.getAbsolutePath (io/file path)) ")"))
   (if (.exists (io/file path))
     (with-open [rdr (io/reader path)]
       (let [spec (json/parse-stream rdr true)]
         (println (str "Successfully parsed OpenAPI spec. Top-level keys count: " (count (keys spec))))
         spec))
     (do
       (println (str "!!! OpenAPI spec file NOT FOUND at: " path " (resolved: " (.getAbsolutePath (io/file path)) ")"))
       nil))))

(defn extract-endpoints
  "Extracts endpoints from the OpenAPI spec. Returns a sequence of maps with method, path, operationId, and docstring."
  [openapi]
  (println "DEBUG: extract-endpoints [openapi] ENTER. Spec nil?" (nil? openapi))
  (let [result (if openapi
                 (do
                   (println "DEBUG: extract-endpoints - openapi keys:" (keys openapi))
                   (println "DEBUG: extract-endpoints - :paths key is map?" (map? (:paths openapi)))
                   (println "DEBUG: extract-endpoints - BEFORE for-loop")
                   (let [endpoints (if (map? (:paths openapi))
                                     (for [[path methods] (:paths openapi)
                                           [method meta] methods]
                                       {:method method
                                        :path path
                                        :operation-id (:operationId meta)
                                        :summary (:summary meta)
                                        :description (:description meta)
                                        :parameters (:parameters meta)
                                        :responses (:responses meta)})
                                     [])]
                     (println "DEBUG: extract-endpoints - AFTER for-loop. Endpoints count:" (count endpoints))
                     endpoints))
                 (do
                   (println "Skipping endpoint extraction because OpenAPI spec is nil.")
                   []))]
    (println "DEBUG: extract-endpoints [openapi] EXIT")
    result))

(defn generate-registry
  "Generate a registry of Kubernetes API operations from the OpenAPI spec.
   Optionally takes a custom spec, otherwise loads the default K8s spec."
  ([spec]
   (let [endpoints (extract-endpoints spec)]
     (->> endpoints
          (map (fn [endpoint]
                 (let [path-key (:path endpoint)
                       method-key (:method endpoint)
                       operation (get-in spec [:paths (keyword path-key) method-key])
                       op-id (or (get operation "operationId")
                                 (get operation :operationId)
                                 (-> path-key str (subs 1) (str/replace #"[^a-zA-Z0-9]" "-")))
                       entry (assoc endpoint
                                    :operation-id op-id
                                    :method (keyword method-key)
                                    :schemas (extract-operation-schemas operation spec))]
                   [(keyword op-id) entry])))
          (into {}))))
  ([]
   (generate-registry (load-openapi-spec))))

(defn extract-operation-schemas
  "Extract request/response schemas for an operation.
   
   Args:
     operation: OpenAPI operation definition
     spec: Full OpenAPI specification
     
   Returns:
     Map with :request and :response schema metadata"
  [operation spec]
  (let [request-schema (extract-request-schema operation spec)
        response-schema (extract-response-schema operation spec)]
    (cond-> {}
      request-schema (assoc :request request-schema)
      response-schema (assoc :response response-schema))))

(defn extract-request-schema
  "Extract request body schema from operation parameters.
   
   Args:
     operation: OpenAPI operation definition
     spec: Full OpenAPI specification
     
   Returns:
     Schema reference map or nil"
  [operation spec]
  (when-let [request-body (get operation "requestBody")]
    (get-in request-body ["content" "application/json" "schema"])))

(defn extract-response-schema
  "Extract success response schema from operation responses.
   
   Args:
     operation: OpenAPI operation definition  
     spec: Full OpenAPI specification
     
   Returns:
     Schema reference map or nil"
  [operation spec]
  (let [responses (get operation "responses")]
    (or (get-in responses ["200" "content" "application/json" "schema"])
        (get-in responses ["201" "content" "application/json" "schema"])
        (get-in responses ["202" "content" "application/json" "schema"]))))

(defn generate-registry
  "Generates a registry map of operation keywords to endpoint metadata."
  ([]
   (println "DEBUG: generate-registry [] ENTER")
   (let [result (generate-registry (load-openapi-spec))]
     (println "DEBUG: generate-registry [] EXIT")
     result))
  ([openapi]
   (println "DEBUG: generate-registry [openapi] ENTER. Spec nil?" (nil? openapi))
   (let [entries (if openapi
                   (->> (extract-endpoints openapi)
                        (map (fn [ep]
                               (let [op-id (:operation-id ep)
                                     key (if op-id
                                           (keyword op-id)
                                           (keyword (str (name (:method ep)) "-"
                                                         (clojure.string/replace (:path ep) #"[/{}]" "-"))))
                                     ; Convert method to keyword
                                     ep-with-method-kw (assoc ep :method (keyword (:method ep)))]
                                 [key ep-with-method-kw])))
                        (into {}))
                   {})]
     (println "DEBUG: generate-registry [openapi] EXIT. Registry size:" (count entries))
     entries)))

(defonce registry (delay (generate-registry)))

(defn get-registry
  "Returns the registry map."
  []
  @registry)

(defn lookup
  "Lookup endpoint metadata by operation keyword."
  [op]
  (get (get-registry) op))

;; Find operations by name or pattern
(defn find-operation
  "Find an operation in the registry by name or pattern.
   Returns the operation keyword or nil if not found.
   
   Examples:
   ```clojure
   (find-operation :read-namespaced-pod)
   (find-operation \"readNamespacedPod\")
   ```"
  [name-or-pattern]
  (let [registry (get-registry)
        name-str (if (keyword? name-or-pattern)
                   (name name-or-pattern)
                   (str name-or-pattern))
        matches (filter (fn [[k v]]
                          (let [op-id (:operation-id v)
                                k-name (name k)]
                            (or (and op-id (= op-id name-str))
                                (= k-name name-str)
                                (= k name-or-pattern))))
                        registry)]
    (when (seq matches)
      (ffirst matches))))

(defn find-operation-by-kind
  "Find an operation in the registry by HTTP method and resource kind.
   Returns the operation keyword or nil if not found.
   
   Parameters:
   - method: The HTTP method (:get, :post, :put, :delete, etc.)
   - kind: The resource kind (e.g., \"Pod\", \"Deployment\")
   
   Examples:
   ```clojure
   (find-operation-by-kind :get \"Pod\")
   (find-operation-by-kind :list \"Deployment\")
   ```"
  [operation-type kind]
  (let [registry (get-registry)
        op-pattern (case operation-type
                     :get #"^read.*"
                     :list #"^list.*"
                     :create #"^create.*"
                     :update #"^(replace|patch).*"
                     :delete #"^delete.*"
                     (throw (ex-info (str "Unknown operation type: " operation-type)
                                     {:operation-type operation-type})))
        kind-lower (str/lower-case kind)
        matches (filter (fn [[_ v]]
                          (let [op-id (:operation-id v)]
                            (and op-id ; Check that op-id is not nil
                                 (string? op-id) ; Ensure it's a string
                                 (re-find op-pattern op-id)
                                 (re-find (re-pattern (str "(?i)" kind-lower)) op-id))))
                        registry)]
    (when (seq matches)
      (ffirst matches))))

(defn ^:private normalize-path
  "Normalize the path by removing the leading slash and replacing non-alphanumeric characters with hyphens."
  [path]
  (-> path
      (subs 1)
      (str/replace #"[^a-zA-Z0-9]" "-")))

(defn ^:private operation-id->keyword
  "Convert the operation ID to a keyword, normalizing the path and HTTP method."
  [operation-id method path]
  (let [normalized-path (normalize-path path)
        method-str (name method)]
    (keyword (str method-str "-" normalized-path))))

(defn ^:private extract-operation-id
  "Extract the operation ID from the operation map, path, and method."
  [operation path method]
  (or (get operation "operationId")
      (get operation :operationId)
      (-> path str (subs 1) (str/replace #"[^a-zA-Z0-9]" "-"))))
