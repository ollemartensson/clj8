(ns clj8.core.core
  (:require [clj8.client.client :as client]
            [clj8.registry.registry :as registry]))

;; Connection & Context Management
(defonce ^:private current-context (atom nil))

(defn connect!
  "Establish a connection to a Kubernetes cluster.
   
   Options map includes:
   - :server - The Kubernetes API server URL
   - :token - The authentication token
   - :insecure? - Whether to skip TLS verification (default: false)
   - :context-name - Optional name for this context

   Returns the connection context map that can be used with other functions.
   
   Examples:
   ```clojure
   ;; Connect using explicit credentials
   (connect! {:server \"https://k8s.example.com\" :token \"my-token\"})
   
   ;; Connect using KUBECONFIG environment
   (connect!)
   ```"
  ([]
   (connect! {}))
  ([options]
   (let [context {:server (or (:server options)
                              (System/getenv "KUBE_API_SERVER")
                              "http://localhost:8080")
                  :token (or (:token options)
                             (System/getenv "KUBE_TOKEN"))
                  :insecure? (:insecure? options false)
                  :context-name (or (:context-name options)
                                    "default")}]
     (reset! current-context context)
     context)))

(defn current-connection
  "Get the current connection context."
  []
  @current-context)

;; Generic Resource Operations
(defn get-resource
  "Get a Kubernetes resource by kind, name, and namespace (if applicable).
   
   Parameters:
   - kind: The resource kind (e.g., \"Pod\", \"Deployment\")
   - name: The resource name
   - namespace: The namespace (optional for cluster-level resources)
   - opts: Additional options map

   Examples:
   ```clojure
   (get-resource \"Pod\" \"my-pod\" \"default\")
   (get-resource \"Namespace\" \"kube-system\" nil)
   ```"
  [kind name namespace & [opts]]
  (let [ctx (or (:context opts) @current-context)
        op-keyword (registry/find-operation-by-kind :get kind)
        params (cond-> {:name name
                        :kube-api-server (:server ctx)
                        :kube-token (:token ctx)}
                 namespace (assoc :namespace namespace))]
    (client/invoke op-keyword params)))

(defn list-resources
  "List Kubernetes resources by kind and namespace (if applicable).
   
   Parameters:
   - kind: The resource kind (e.g., \"Pod\", \"Deployment\")
   - namespace: The namespace (optional for cluster-level resources)
   - opts: Additional options map with :label-selector, :field-selector, etc.

   Examples:
   ```clojure
   (list-resources \"Pod\" \"default\")
   (list-resources \"Pod\" \"default\" {:label-selector \"app=myapp\"})
   (list-resources \"Namespace\" nil)
   ```"
  [kind namespace & [opts]]
  (let [ctx (or (:context opts) @current-context)
        op-keyword (registry/find-operation-by-kind :list kind)
        base-params (cond-> {:kube-api-server (:server ctx)
                             :kube-token (:token ctx)}
                      namespace (assoc :namespace namespace))
        params (merge base-params (dissoc opts :context))]
    (client/invoke op-keyword params)))

(defn create-resource
  "Create a Kubernetes resource.
   
   Parameters:
   - resource: A map representing the resource to create
   - namespace: The namespace (optional for cluster-level resources)
   - opts: Additional options map

   Examples:
   ```clojure
   (create-resource {:kind \"Pod\" 
                     :metadata {:name \"my-pod\"} 
                     :spec {...}} 
                    \"default\")
   ```"
  [resource namespace & [opts]]
  (let [ctx (or (:context opts) @current-context)
        kind (:kind resource)
        op-keyword (registry/find-operation-by-kind :create kind)
        base-params (cond-> {:body resource
                             :kube-api-server (:server ctx)
                             :kube-token (:token ctx)}
                      namespace (assoc :namespace namespace))
        params (merge base-params (dissoc opts :context))]
    (client/invoke op-keyword params)))

(defn update-resource
  "Update a Kubernetes resource.
   
   Parameters:
   - resource: A map representing the resource to update
   - opts: Additional options map

   Examples:
   ```clojure
   (update-resource {:kind \"Pod\" 
                     :metadata {:name \"my-pod\" :namespace \"default\"} 
                     :spec {...}})
   ```"
  [resource & [opts]]
  (let [ctx (or (:context opts) @current-context)
        kind (:kind resource)
        name (-> resource :metadata :name)
        namespace (-> resource :metadata :namespace)
        op-keyword (registry/find-operation-by-kind :update kind)
        base-params (cond-> {:body resource
                             :name name
                             :kube-api-server (:server ctx)
                             :kube-token (:token ctx)}
                      namespace (assoc :namespace namespace))
        params (merge base-params (dissoc opts :context))]
    (client/invoke op-keyword params)))

(defn delete-resource
  "Delete a Kubernetes resource.
   
   Parameters:
   - kind: The resource kind (e.g., \"Pod\", \"Deployment\")
   - name: The resource name
   - namespace: The namespace (optional for cluster-level resources)
   - opts: Additional options map

   Examples:
   ```clojure
   (delete-resource \"Pod\" \"my-pod\" \"default\")
   (delete-resource \"Namespace\" \"my-namespace\" nil)
   ```"
  [kind name namespace & [opts]]
  (let [ctx (or (:context opts) @current-context)
        op-keyword (registry/find-operation-by-kind :delete kind)
        base-params (cond-> {:name name
                             :kube-api-server (:server ctx)
                             :kube-token (:token ctx)}
                      namespace (assoc :namespace namespace))
        params (merge base-params (dissoc opts :context))]
    (client/invoke op-keyword params)))

;; Pod-specific Operations
(defn get-pod-logs
  "Get logs for a pod.
   
   Parameters:
   - name: The pod name
   - namespace: The namespace
   - opts: Additional options map with :container, :previous, :tail-lines, etc.

   Examples:
   ```clojure
   (get-pod-logs \"my-pod\" \"default\")
   (get-pod-logs \"my-pod\" \"default\" {:container \"sidecar\" :tail-lines 100})
   ```"
  [name namespace & [opts]]
  (let [ctx (or (:context opts) @current-context)
        op-keyword (registry/find-operation :read-namespaced-pod-log)
        base-params (cond-> {:name name
                             :namespace namespace
                             :kube-api-server (:server ctx)
                             :kube-token (:token ctx)}
                      (:container opts) (assoc :container (:container opts))
                      (:previous opts) (assoc :previous (:previous opts))
                      (:tail-lines opts) (assoc :tailLines (:tail-lines opts)))
        params (merge base-params (dissoc opts :context :container :previous :tail-lines))]
    (client/invoke op-keyword params)))

;; Deployment-specific Operations
(defn scale-deployment
  "Scale a deployment to the specified number of replicas.
   
   Parameters:
   - name: The deployment name
   - namespace: The namespace
   - replicas: The desired number of replicas
   - opts: Additional options map

   Examples:
   ```clojure
   (scale-deployment \"my-deployment\" \"default\" 3)
   ```"
  [name namespace replicas & [opts]]
  (let [ctx (or (:context opts) @current-context)
        deployment (get-resource "Deployment" name namespace opts)
        updated-deployment (assoc-in deployment [:spec :replicas] replicas)]
    (update-resource updated-deployment opts)))

;; Namespace Operations
(defn create-namespace
  "Create a new namespace.
   
   Parameters:
   - name: The namespace name
   - opts: Additional options map

   Examples:
   ```clojure
   (create-namespace \"my-namespace\")
   (create-namespace \"my-namespace\" {:labels {:env \"dev\"}})
   ```"
  [name & [opts]]
  (let [ctx (or (:context opts) @current-context)
        labels (get opts :labels {})
        resource {:apiVersion "v1"
                  :kind "Namespace"
                  :metadata {:name name
                             :labels labels}}]
    (create-resource resource nil opts)))

;; Utility Functions
(defn watch-resources
  "Watch Kubernetes resources for changes.
   This is a placeholder for future implementation.
   
   Parameters:
   - kind: The resource kind (e.g., \"Pod\", \"Deployment\")
   - namespace: The namespace (optional for cluster-level resources)
   - callback: A function to call when a change is detected
   - opts: Additional options map

   Examples:
   ```clojure
   (watch-resources \"Pod\" \"default\" 
                    (fn [event] (println \"Event:\" event)))
   ```"
  [kind namespace callback & [opts]]
  (throw (ex-info "Not implemented yet"
                  {:kind kind :namespace namespace :callback callback :opts opts})))

;; API Discovery Functions
(defn list-available-operations
  "List available Kubernetes API operations by kind or pattern.
   
   Parameters:
   - filter-by: Optional kind or pattern to filter by (e.g., \"Pod\", \"Deployment\", \"list\")
   
   Returns a sequence of maps with operation details.
   
   Examples:
   ```clojure
   ;; List all operations
   (list-available-operations)
   
   ;; List all Pod operations
   (list-available-operations \"Pod\")
   
   ;; List all list operations
   (list-available-operations \"list\")
   ```"
  ([]
   (list-available-operations nil))
  ([filter-by]
   (let [registry (registry/get-registry)
         pattern (when filter-by
                   (re-pattern (str "(?i)" (name filter-by))))
         filtered (if pattern
                    (filter (fn [[k v]]
                              (or (re-find pattern (name k))
                                  (re-find pattern (:operation-id v))
                                  (re-find pattern (str (:path v)))))
                            registry)
                    registry)]
     (map (fn [[k v]]
            {:keyword k
             :operation-id (:operation-id v)
             :method (:method v)
             :path (:path v)
             :summary (:summary v)})
          filtered))))

(defn explain-resource-kind
  "Get information about a specific resource kind.
   
   Parameters:
   - kind: The resource kind (e.g., \"Pod\", \"Deployment\")
   
   Returns a map with available operations for the specified kind.
   
   Examples:
   ```clojure
   (explain-resource-kind \"Pod\")
   ```"
  [kind]
  (let [operations (list-available-operations kind)
        grouped-by-method (group-by :method operations)]
    {:kind kind
     :get (get grouped-by-method :get)
     :list (get grouped-by-method :list)
     :create (get grouped-by-method :post)
     :update (get grouped-by-method :put)
     :patch (get grouped-by-method :patch)
     :delete (get grouped-by-method :delete)}))