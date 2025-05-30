(ns clj8.api
  "High-level API for clj8 Kubernetes client.
   
   This namespace provides the main public API for using clj8 in other projects."
  (:require [clj8.registry.registry :as reg]
            [clj8.client.client :as client]
            [clj8.malli.malli :as malli]
            [clj8.macros.macros :as macros]
            [clojure.string :as str]))

;; Global configuration state
(defonce ^:private config (atom {}))

(defn configure!
  "Configure clj8 for use in your application.
   
   Options:
   - :server - Kubernetes API server URL
   - :token - Authentication token
   - :context - kubectl context to use
   - :namespace - Default namespace
   - :timeout - Request timeout in ms
   - :ca-cert - Path to CA certificate file
   - :insecure? - Skip TLS verification
   - :middleware - Custom middleware chain
   
   Examples:
   (configure! {:server \"https://my-cluster.com\" :token \"abc123\"})
   (configure! {:context \"my-context\" :namespace \"production\"})
   (configure! {}) ; Use current kubectl context"
  [options]
  (reset! config (merge {:namespace "default"
                         :timeout 30000
                         :insecure? false}
                        options))
  @config)

(defn current-config
  "Get current clj8 configuration."
  []
  @config)

(defn close!
  "Close clj8 client and clean up resources."
  []
  (reset! config {}))

;; Helper functions for common operations
(defn- merge-config-params
  "Merge global config with operation params."
  [params]
  (merge @config params))

(defn- invoke-operation
  "Invoke a K8s operation with merged configuration."
  [op-keyword params]
  (client/invoke op-keyword (merge-config-params params)))

;; Core API functions - these would be generated by macros in a real implementation
;; For now, providing key examples manually

(defn list-pods
  "List pods in a namespace.
   
   Args:
   - params: Map with :namespace and optional query parameters
   
   Examples:
   (list-pods {:namespace \"default\"})
   (list-pods {:namespace \"kube-system\" :labelSelector \"app=nginx\"})"
  ([params]
   (invoke-operation :listCoreV1NamespacedPod params))
  ([]
   (list-pods {:namespace (:namespace @config "default")})))

(defn get-pod
  "Get a specific pod.
   
   Args:
   - params: Map with :namespace and :name
   
   Example:
   (get-pod {:namespace \"default\" :name \"my-pod\"})"
  [params]
  (invoke-operation :readCoreV1NamespacedPod params))

(defn create-pod
  "Create a new pod.
   
   Args:
   - params: Map with :namespace and :body (pod specification)
   
   Example:
   (create-pod {:namespace \"default\"
                :body {:apiVersion \"v1\" :kind \"Pod\" ...}})"
  [params]
  (invoke-operation :createCoreV1NamespacedPod params))

(defn delete-pod
  "Delete a pod.
   
   Args:
   - params: Map with :namespace and :name
   
   Example:
   (delete-pod {:namespace \"default\" :name \"my-pod\"})"
  [params]
  (invoke-operation :deleteCoreV1NamespacedPod params))

(defn list-deployments
  "List deployments in a namespace.
   
   Args:
   - params: Map with :namespace and optional query parameters
   
   Example:
   (list-deployments {:namespace \"default\"})"
  ([params]
   (invoke-operation :listAppsV1NamespacedDeployment params))
  ([]
   (list-deployments {:namespace (:namespace @config "default")})))

(defn get-deployment
  "Get a specific deployment."
  [params]
  (invoke-operation :readAppsV1NamespacedDeployment params))

(defn create-deployment
  "Create a new deployment."
  [params]
  (invoke-operation :createAppsV1NamespacedDeployment params))

(defn patch-deployment
  "Patch an existing deployment."
  [params]
  (invoke-operation :patchAppsV1NamespacedDeployment params))

(defn delete-deployment
  "Delete a deployment."
  [params]
  (invoke-operation :deleteAppsV1NamespacedDeployment params))

(defn list-services
  "List services in a namespace."
  ([params]
   (invoke-operation :listCoreV1NamespacedService params))
  ([]
   (list-services {:namespace (:namespace @config "default")})))

(defn get-service
  "Get a specific service."
  [params]
  (invoke-operation :readCoreV1NamespacedService params))

(defn create-service
  "Create a new service."
  [params]
  (invoke-operation :createCoreV1NamespacedService params))

(defn delete-service
  "Delete a service."
  [params]
  (invoke-operation :deleteCoreV1NamespacedService params))

(defn list-namespaces
  "List all namespaces."
  ([]
   (invoke-operation :listCoreV1Namespace {})))

(defn get-namespace
  "Get a specific namespace."
  [params]
  (invoke-operation :readCoreV1Namespace params))

(defn create-namespace
  "Create a new namespace."
  [params]
  (invoke-operation :createCoreV1Namespace params))

(defn delete-namespace
  "Delete a namespace."
  [params]
  (invoke-operation :deleteCoreV1Namespace params))

;; Configuration management
(defn create-config-map
  "Create a ConfigMap."
  [params]
  (invoke-operation :createCoreV1NamespacedConfigMap params))

(defn get-config-map
  "Get a ConfigMap."
  [params]
  (invoke-operation :readCoreV1NamespacedConfigMap params))

(defn create-secret
  "Create a Secret."
  [params]
  (invoke-operation :createCoreV1NamespacedSecret params))

(defn get-secret
  "Get a Secret."
  [params]
  (invoke-operation :readCoreV1NamespacedSecret params))

;; Events and monitoring
(defn list-events
  "List events in a namespace."
  ([params]
   (invoke-operation :listCoreV1NamespacedEvent params))
  ([]
   (list-events {:namespace (:namespace @config "default")})))

(defn watch-events
  "Watch for events in a namespace.
   
   Args:
   - params: Map with :namespace
   - callback: Function called for each event
   
   Example:
   (watch-events {:namespace \"default\"}
                 (fn [event] (println \"Event:\" event)))"
  [params callback]
  ;; This would be implemented with server-sent events or websockets
  (throw (ex-info "Watch operations not yet implemented" {:params params})))

(defn watch-pods
  "Watch for pod changes.
   
   Args:
   - params: Map with :namespace  
   - callback: Function called for each change event"
  [params callback]
  (throw (ex-info "Watch operations not yet implemented" {:params params})))

;; Schema and validation utilities
(defn validate-resource
  "Validate a Kubernetes resource using Malli schemas.
   
   Args:
   - resource-type: Keyword like :Pod, :Service, :Deployment
   - data: Resource data to validate
   
   Returns:
   Map with :valid? boolean and :errors if invalid
   
   Example:
   (validate-resource :Pod my-pod-data)"
  [resource-type data]
  (if-let [schema (malli/get-schema resource-type)]
    (malli/validate-with-schema schema data)
    {:valid? false :errors [(str "No schema found for " resource-type)]}))

(defn generate-sample
  "Generate sample data for a Kubernetes resource type.
   
   Args:
   - resource-type: Keyword like :Pod, :Service, :Deployment
   
   Returns:
   Generated sample data
   
   Example:
   (generate-sample :Service)"
  [resource-type]
  (if-let [schema (malli/get-schema resource-type)]
    (malli/generate-sample schema)
    (throw (ex-info (str "No schema found for " resource-type)
                    {:resource-type resource-type}))))

;; Registry inspection
(defn list-operations
  "List all available Kubernetes API operations.
   
   Returns:
   Vector of operation metadata maps"
  []
  (let [registry (reg/get-registry)]
    (->> registry
         (map (fn [[op-keyword op-meta]]
                {:operation op-keyword
                 :method (:method op-meta)
                 :path (:path op-meta)
                 :summary (:summary op-meta)}))
         (sort-by :operation))))

(defn describe-operation
  "Get detailed information about a specific operation.
   
   Args:
   - op-keyword: Operation keyword
   
   Returns:
   Operation metadata map"
  [op-keyword]
  (reg/lookup op-keyword))

;; Async operations (future enhancement)
(defn list-pods-async
  "Async version of list-pods. Returns a core.async channel."
  [params]
  (throw (ex-info "Async operations not yet implemented" {:params params})))

;; Utility functions
(defn healthy?
  "Check if a pod is healthy (running and ready).
   
   Args:
   - pod: Pod data map
   
   Returns:
   Boolean indicating if pod is healthy"
  [pod]
  (and (= "Running" (get-in pod [:status :phase]))
       (every? :ready (get-in pod [:status :containerStatuses] []))))

(defn resource-summary
  "Get a summary of resources in a namespace.
   
   Args:
   - namespace: Namespace name
   
   Returns:
   Map with resource counts"
  [namespace]
  {:pods (count (list-pods {:namespace namespace}))
   :services (count (list-services {:namespace namespace}))
   :deployments (count (list-deployments {:namespace namespace}))})

;; Development and debugging helpers
(defn debug-request
  "Enable debug logging for the next request."
  []
  (swap! config assoc :debug? true))

(defn version
  "Get clj8 version information."
  []
  {:version "0.1.0"
   :operations (count (reg/get-registry))
   :schemas (count (malli/extract-schemas (reg/load-openapi-spec)))})

(comment
  ;; Usage examples

  ;; Basic setup
  (configure! {:server "https://my-cluster.com" :token "abc123"})

  ;; List resources
  (list-pods {:namespace "default"})
  (list-deployments)

  ;; Create resources
  (create-pod {:namespace "default"
               :body {:apiVersion "v1"
                      :kind "Pod"
                      :metadata {:name "test-pod"}
                      :spec {:containers [{:name "test" :image "nginx"}]}}})

  ;; Validation
  (validate-resource :Pod my-pod-data)
  (generate-sample :Service)

  ;; Introspection
  (list-operations)
  (describe-operation :listCoreV1NamespacedPod)
  (version))