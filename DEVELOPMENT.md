# clj8 Development Guide

## Setup

1. **Download OpenAPI spec**:
   ```bash
   chmod +x scripts/dev.sh
   ./scripts/dev.sh
   ```

2. **Start REPL**:
   ```bash
   clj -M:dev
   ```

## Development Workflow

### 1. REPL-driven Development
```clojure
;; Load user namespace with all helpers
(require '[user :as u])

;; Explore registry
(count u/registry)
(first u/registry)

;; Test API functions
(require '[clj8.api.api :as k8s])
(doc k8s/list-core-v1-namespaced-pod)

;; Validate schemas
(require '[clj8.malli.malli :as malli])
(malli/get-malli-schema :io.k8s.api.core.v1.Pod)
```

### 2. Testing
```bash
# All tests
clj -M:test

# Specific bricks
clj -M:test --include :registry
clj -M:test --include :malli  

# Watch mode (requires entr)
find . -name "*.clj" | entr -c clj -M:test
```

### 3. Code Quality
```bash
# Lint with clj-kondo
clj-kondo --lint bricks bases development

# Check Polylith workspace
clj -M:poly info
clj -M:poly check
```

## Architecture Overview

### Bricks (Components)
- **registry**: OpenAPI spec → operation registry
- **malli**: Schema conversion & validation  
- **client**: HTTP client implementation
- **macros**: Function generation from registry
- **core**: High-level convenience functions

### Data Flow
```
OpenAPI Spec → Registry → Malli Schemas → Generated Functions → HTTP Client
```

### Key Namespaces
- `clj8.registry.registry` - Core operation registry
- `clj8.malli.malli` - Schema conversion  
- `clj8.client.client` - HTTP invocation
- `clj8.macros.macros` - Function generation
- `clj8.api.api` - Generated public API

## Adding New Features

### 1. Registry Enhancement
Edit `bricks/registry/src/clj8/registry/registry.clj`:
- Add new operation parsing logic
- Update `extract-endpoints` function
- Add tests in `bricks/registry/test/`

### 2. Schema Support  
Edit `bricks/malli/src/clj8/malli/malli.clj`:
- Extend `convert-schema*` for new types
- Add validation helpers
- Test in `bricks/malli/test/`

### 3. Client Features
Edit `bricks/client/src/clj8/client/client.clj`:
- Add middleware or auth options
- Extend `invoke` function
- Add integration tests

## Testing Strategy

### Unit Tests
- Each brick has isolated tests
- Mock external dependencies
- Fast feedback loop

### Integration Tests  
- Test brick interactions
- Use real OpenAPI specs
- Validate generated functions

### Property-based Tests
- Generate random K8s resources
- Test schema roundtrips
- Validate API contracts

### Example Test
```clojure
(deftest registry-malli-integration-test
  (testing "Registry and Malli work together"
    (let [op-key :io.k8s.api.core.v1.listNamespacedPod
          op-details (reg/lookup op-key)
          schema (malli/get-malli-schema :io.k8s.api.core.v1.PodList)]
      (is (some? op-details))
      (is (malli/valid? schema test-pod-list-data)))))
```

## Troubleshooting

### Common Issues

1. **Missing OpenAPI spec**
   ```bash
   ./scripts/dev.sh  # Downloads spec
   ```

2. **Registry empty** 
   ```clojure
   (reg/load-openapi-spec "resources/k8s-openapi.json")
   ```

3. **Malli schemas not found**
   ```clojure
   (require '[clj8.malli.malli :as malli])
   (malli/openapi->malli-registry "resources/k8s-openapi.json")
   ```

4. **Functions not generated**
   ```clojure
   (require '[clj8.api.api :as k8s] :reload)
   ```

### Debug Commands
```clojure
;; Check registry state
(count (reg/get-registry))

;; Inspect operation
(reg/lookup :io.k8s.api.core.v1.listNamespacedPod)

;; Check malli registry
(keys (mr/schemas @malli/malli-registry))

;; Test HTTP client
(client/invoke :io.k8s.api.core.v1.listNamespacedPod {:namespace "default"})
```
