# clj8

![clj8 Logo](resources/clj8-logo.png)

**Clojure-native, data-driven Kubernetes toolkit**

A modular Kubernetes client library built with [Polylith](https://polylith.gitbook.io/polylith/) architecture, featuring:

- 🎯 **Data-driven API** - All operations defined by OpenAPI spec data
- 🔧 **Two API layers** - Low-level data functions + high-level generated macros  
- 📋 **Rich metadata** - Full docstrings, schemas, and validation via Malli
- 🧩 **Modular design** - Mix and match bricks as needed
- 🚀 **Cross-platform** - JVM, CLJS, and Babashka support

## Quick Start

```bash
# Clone and setup
git clone <repo-url> && cd clj8

# Download Kubernetes OpenAPI spec
curl -o resources/k8s-openapi.json https://raw.githubusercontent.com/kubernetes/kubernetes/master/api/openapi-spec/swagger.json

# Start development REPL
clj -M:dev
```

```clojure
;; In the REPL
(require '[clj8.api.api :as k8s])

;; List pods in default namespace  
(k8s/list-core-v1-namespaced-pod {:namespace "default"})

;; Get specific pod
(k8s/read-core-v1-namespaced-pod {:namespace "default" :name "my-pod"})

;; Create a pod
(k8s/create-core-v1-namespaced-pod 
  {:namespace "default"
   :body {:apiVersion "v1"
          :kind "Pod" 
          :metadata {:name "test-pod"}
          :spec {:containers [{:name "test" :image "nginx"}]}}})
```

## Architecture

```
├── bricks/           # Reusable components
│   ├── registry/     # OpenAPI → data registry  
│   ├── malli/        # Schema validation & generation
│   ├── client/       # HTTP client (platform-agnostic)
│   ├── macros/       # Function generation
│   └── core/         # High-level convenience API
├── bases/api/        # Public API aggregation
└── projects/dev/     # Development & testing
```

## Development

```bash
# Run tests
clj -M:test

# Check specific brick
clj -M:test --include :registry

# Start dev REPL with all bricks loaded
clj -M:dev
```

## API Layers

### 1. Data-driven (Low-level)
```clojure
(require '[clj8.client.client :as client])

;; Direct operation invocation
(client/invoke :io.k8s.api.core.v1.listNamespacedPod 
               {:namespace "default"})
```

### 2. Generated Functions (High-level) 
```clojure
(require '[clj8.api.api :as k8s])

;; Generated function with full metadata
(k8s/list-core-v1-namespaced-pod {:namespace "default"})

;; Inspect function metadata
(doc k8s/list-core-v1-namespaced-pod)
(meta #'k8s/list-core-v1-namespaced-pod)
```

## Schema Validation

```clojure
(require '[clj8.malli.malli :as malli])

;; Get Malli schema for any K8s resource
(malli/get-malli-schema :io.k8s.api.core.v1.Pod)

;; Validate data
(malli/validate :io.k8s.api.core.v1.Pod my-pod-data)

;; Generate test data
(require '[malli.generator :as mg])
(mg/generate (malli/get-malli-schema :io.k8s.api.core.v1.Pod))
```

## Configuration

Set Kubernetes context via environment variables:
```bash
export KUBE_API_SERVER="https://my-cluster-api"
export KUBE_TOKEN="your-token-here" 
# OR use existing KUBECONFIG
export KUBECONFIG="~/.kube/config"
```

## Testing

```bash
# All tests
clj -M:test

# Specific brick tests  
clj -M:test --include :registry
clj -M:test --include :malli
clj -M:test --include :client

# Property-based testing with generated data
clj -M:test --include :generative
```

## License

MIT License