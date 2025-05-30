; filepath: /Users/olle/src/clj8/projects/dev/src/user.clj
(ns user
  (:require [clojure.repl :refer [doc]] ; Only refer to 'doc' for now
            [clojure.pprint :refer [pprint]]
            [clj8.registry.registry :as reg]
            [clj8.malli.malli :as k8s-malli]
            [malli.registry :as mr] ; Added for inspecting the lazy registry
            ; [clj8.client.client :as client] ; For direct invoke, if needed
            [clj8.api.api :as k8s])) ; This ns will have all generated fns

;; Note: When running in dev-test mode, you can load test utilities with:
;; (require '[dev-test :as dt])

(println "Welcome to clj8 dev REPL!")
(println "Loading Kubernetes API registry...")
(def registry (reg/get-registry))
(println (str (count registry) " API operations loaded into registry.")) ; Removed @, assuming get-registry returns a map directly

(println "K8s Malli lazy registry is available via k8s-malli/malli-registry.")
;; No longer building all schemas upfront, they are loaded lazily.
;; (def malli-schemas (k8s-malli/build-malli-schemas-from-openapi))
;; (println (str (count malli-schemas) " Malli schemas generated."))

(println "All clj8 API functions (e.g., k8s/list-namespaced-pod) are available under the 'k8s' alias.")
(println "Try: (doc k8s/read-core-v1-namespaced-pod)")
(println "Or: (k8s/list-core-v1-namespaced-pod {:namespace \"default\"})")

(comment
  ;; Common REPL tasks:

  ;; 1. Explore the registry
  (count registry) ; Removed @
  (first registry) ; Removed @
  (reg/lookup :io.k8s.api.core.v1.listNamespacedPod) ; Use actual op-id

  ;; 2. Inspect generated API functions (assuming defk8sapi-fns has run in clj8.api.api)
  (doc k8s/read-core-v1-namespaced-pod) ; Replace with an actual generated function name
  (meta #'k8s/read-core-v1-namespaced-pod)

  ;; 3. List available API functions (they are in clj8.api.api ns)
  (->> (ns-publics 'clj8.api.api)
       (filter (fn [[_ v]] (fn? (deref v)))) ; Filter for functions
       (map (fn [[s _]] s))
       (sort)
       (pprint))

  ;; 4. Explore Malli schemas (now using the lazy registry)
  ;; To see currently resolved schemas in the lazy registry:
  (keys (mr/schemas @k8s-malli/malli-registry))
  ;; To get a specific schema (will be resolved on demand):
  (k8s-malli/get-malli-schema :io.k8s.api.core.v1.Pod)
  (k8s-malli/explain :io.k8s.api.core.v1.Pod {:spec {:containers "not a list"}})

  ;; 5. Invoke an API (ensure KUBECONFIG or KUBE_API_SERVER/KUBE_TOKEN are set)
  ;; Replace with an actual generated function and valid parameters.
  ;; Example (this specific function name might vary based on your generation logic):
  ; (k8s/list-core-v1-namespaced-pod {:namespace "default"})

  ;; 6. Testing with the dev-test namespace
  ;; First load the dev-test namespace
  (require '[dev-test :as dt])

  ;; Run all tests
  (dt/run-all-tests)

  ;; Run tests for a specific brick
  (dt/run-brick-tests :registry)

  ;; List all test namespaces
  (dt/list-test-namespaces)

  ;; Find all test files
  (dt/find-test-files)
  ; (k8s/read-core-v1-namespaced-pod {:namespace "default" :name "some-pod-name"})

  ;; If you need to call invoke directly (e.g., for an op not yet in macros or for testing client):
  ; (client/invoke :io.k8s.api.core.v1.listNamespacedPod {:namespace "default"})

  ;; 6. Generative testing with Malli (example)
  (require '[malli.generator :as mg])
  (mg/generate (k8s-malli/get-malli-schema :io.k8s.api.core.v1.Pod)))
