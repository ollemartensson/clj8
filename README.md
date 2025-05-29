# clj8

**Clojure-native, data-driven Kubernetes toolkit**

A modular Kubernetes client library built with [Polylith](https://polylith.gitbook.io/polylith/) architecture, featuring:

- 🎯 **Data-driven API** - All operations defined by OpenAPI spec data
- 🔧 **Two API layers** - Low-level data functions + high-level generated macros  
- 📋 **Rich metadata** - Full docstrings, schemas, and validation via Malli
- 🧩 **Modular design** - Mix and match bricks as needed
- 🚀 **Cross-platform** - JVM, CLJS, and Babashka support

## Installation

### Clojure CLI (deps.edn)
```clojure
{:deps {io.github.clj8/clj8 {:mvn/version "0.1.0"}}}
```

### Leiningen (project.clj)
```clojure
:dependencies [[io.github.clj8/clj8 "0.1.0"]]
```

### Babashka (bb.edn)
```clojure
{:deps {io.github.clj8/clj8 {:mvn/version "0.1.0"}}}
```

## Quick Start

### 1. Basic Setup
```clojure
(require '[clj8.api :as k8s])

;; Configure your cluster connection
(k8s/configure! {:server "https://your-k8s-cluster.com"
                 :token "your-auth-token"})
```

### 2. List Resources
```clojure
;; List all pods in default namespace
(k8s/list-pods {:namespace "default"})

;; List all namespaces
(k8s/list-namespaces)

;; List pods across all namespaces
(k8s/list-pods)
```

### 3. Create Resources
```clojure
;; Create a simple pod
(k8s/create-pod 
  {:namespace "default"
   :body {:apiVersion "v1"
          :kind "Pod"
          :metadata {:name "my-pod"}
          :spec {:containers [{:name "app" 
                               :image "nginx:latest"}]}}})

;; Create from YAML/EDN data
(def my-service-spec 
  {:apiVersion "v1"
   :kind "Service"
   :metadata {:name "my-service" :namespace "default"}
   :spec {:selector {:app "my-app"}
          :ports [{:port 80 :targetPort 8080}]}})

(k8s/create-service {:namespace "default" :body my-service-spec})
```

### 4. Watch and Stream
```clojure
;; Watch pod changes
(k8s/watch-pods {:namespace "default"}
                (fn [event]
                  (println "Pod event:" (:type event) 
                           (get-in event [:object :metadata :name]))))

;; Stream logs
(k8s/stream-pod-logs {:namespace "default" :name "my-pod"}
                     (fn [line] (println "LOG:" line)))
```

## Authentication

### Using kubectl context
```clojure
;; Use current kubectl context (default)
(k8s/configure! {})

;; Use specific kubectl context
(k8s/configure! {:context "my-cluster-context"})
```

### Using service account
```clojure
(k8s/configure! {:server "https://kubernetes.default.svc"
                 :token (slurp "/var/run/secrets/kubernetes.io/serviceaccount/token")
                 :ca-cert "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"})
```

### Using token
```clojure
(k8s/configure! {:server "https://my-cluster.com"
                 :token "eyJhbGciOiJSUzI1NiIs..."})
```

## Library Usage Patterns

### 1. High-level API (Recommended)
```clojure
(require '[clj8.api :as k8s])

;; Generated functions with full metadata and validation
(k8s/list-pods {:namespace "kube-system"})
(k8s/get-pod {:namespace "default" :name "my-pod"})
(k8s/delete-deployment {:namespace "default" :name "my-app"})
```

### 2. Data-driven API (Advanced)
```clojure
(require '[clj8.client :as client])

;; Direct operation invocation with operation keywords
(client/invoke :list-core-v1-namespaced-pod {:namespace "default"})
(client/invoke :read-core-v1-namespaced-pod {:namespace "default" :name "my-pod"})
```

### 3. Schema Validation
```clojure
(require '[clj8.schema :as schema])

;; Validate resources before creation
(schema/valid? :Pod my-pod-data)
; => {:valid? true :data {...}}

;; Generate sample data for testing
(schema/generate :Service)
; => {:apiVersion "v1" :kind "Service" ...}

;; Get schema documentation
(schema/describe :Deployment)
```

### 4. Middleware and Extensions
```clojure
(require '[clj8.middleware :as mw])

;; Add custom middleware
(k8s/configure! {:middleware [(mw/logging)
                              (mw/retry {:max-attempts 3})
                              (mw/metrics {:registry my-metrics})]})

;; Custom authentication
(k8s/configure! {:auth-fn (fn [request] 
                            (assoc-in request [:headers "Authorization"] 
                                      (str "Bearer " (get-fresh-token))))})
```

## Integration Examples

### With Integrant/Component
```clojure
(ns my-app.k8s
  (:require [clj8.api :as k8s]
            [integrant.core :as ig]))

(defmethod ig/init-key :k8s/client [_ config]
  (k8s/configure! config)
  {:api k8s})

(defmethod ig/halt-key! :k8s/client [_ {:keys [api]}]
  (k8s/close! api))

;; config.edn
{:k8s/client {:server "https://my-cluster.com"
              :context "production"}}
```

### With Mount
```clojure
(ns my-app.k8s
  (:require [clj8.api :as k8s]
            [mount.core :as mount]))

(mount/defstate k8s-client
  :start (k8s/configure! {:context "production"})
  :stop (k8s/close! k8s-client))
```

### Async Operations
```clojure
(require '[clojure.core.async :as async])

;; Async pod listing
(async/go
  (let [pods (async/<! (k8s/list-pods-async {:namespace "default"}))]
    (doseq [pod pods]
      (println "Pod:" (get-in pod [:metadata :name])))))

;; Pipeline processing
(let [pod-chan (async/chan 100)
      result-chan (async/chan 100)]
  
  ;; Producer: fetch pods
  (async/go
    (async/>! pod-chan (k8s/list-pods {:namespace "default"}))
    (async/close! pod-chan))
  
  ;; Pipeline: process pods
  (async/pipeline 5 result-chan
                  (map #(assoc % :processed-at (java.time.Instant/now)))
                  pod-chan)
  
  ;; Consumer: handle results
  (async/go-loop []
    (when-let [result (async/<! result-chan)]
      (println "Processed pod:" (get-in result [:metadata :name]))
      (recur))))
```

## Configuration

### Environment Variables
```bash
# Cluster connection
export KUBE_SERVER="https://my-cluster.com"
export KUBE_TOKEN="your-token"
export KUBE_NAMESPACE="default"

# Or use kubeconfig
export KUBECONFIG="~/.kube/config"
export KUBE_CONTEXT="my-context"
```

### Programmatic Configuration
```clojure
;; Global configuration
(k8s/configure! {:server "https://cluster.com"
                 :token "abc123"
                 :namespace "production"
                 :timeout 30000
                 :retry {:max-attempts 3 :backoff-ms 1000}})

;; Per-operation configuration
(k8s/list-pods {:namespace "kube-system"
                :timeout 5000
                :server "https://different-cluster.com"})
```

## Development

See [DEVELOPMENT.md](DEVELOPMENT.md) for:
- Setting up development environment
- Running tests
- Contributing guidelines
- Architecture overview

## Examples

### Deployment Management
```clojure
(def deployment-spec
  {:apiVersion "apps/v1"
   :kind "Deployment"
   :metadata {:name "my-app" :namespace "default"}
   :spec {:replicas 3
          :selector {:matchLabels {:app "my-app"}}
          :template {:metadata {:labels {:app "my-app"}}
                     :spec {:containers [{:name "app"
                                          :image "my-app:latest"
                                          :ports [{:containerPort 8080}]}]}}}})

;; Create deployment
(k8s/create-deployment {:namespace "default" :body deployment-spec})

;; Scale deployment
(k8s/patch-deployment {:namespace "default" 
                       :name "my-app"
                       :body {:spec {:replicas 5}}})

;; Rolling update
(k8s/patch-deployment {:namespace "default"
                       :name "my-app" 
                       :body {:spec {:template {:spec {:containers [{:name "app"
                                                                     :image "my-app:v2"}]}}}}})
```

### Configuration Management
```clojure
;; Create ConfigMap
(k8s/create-config-map
  {:namespace "default"
   :body {:apiVersion "v1"
          :kind "ConfigMap"
          :metadata {:name "app-config"}
          :data {"database.url" "jdbc:postgresql://db:5432/myapp"
                 "log.level" "INFO"}}})

;; Create Secret
(k8s/create-secret
  {:namespace "default"
   :body {:apiVersion "v1"
          :kind "Secret"
          :metadata {:name "app-secrets"}
          :type "Opaque"
          :data {"password" (-> "secret123" .getBytes java.util.Base64/getEncoder .encodeToString)}}})
```

### Monitoring and Observability
```clojure
;; Check pod health
(defn healthy-pods [namespace]
  (->> (k8s/list-pods {:namespace namespace})
       (filter #(= "Running" (get-in % [:status :phase])))
       (filter #(every? true? (map :ready (get-in % [:status :containerStatuses]))))))

;; Get resource usage
(defn resource-usage [namespace]
  {:pods (count (k8s/list-pods {:namespace namespace}))
   :services (count (k8s/list-services {:namespace namespace}))
   :deployments (count (k8s/list-deployments {:namespace namespace}))})

;; Watch for events
(k8s/watch-events {:namespace "default"}
                  (fn [event]
                    (when (= "Warning" (:type event))
                      (log/warn "K8s Warning:" (:reason event) (:message event)))))
```

## License

MIT License

## Links

- [GitHub Repository](https://github.com/clj8/clj8)
- [API Documentation](https://clj8.github.io/clj8/)
- [Changelog](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md)