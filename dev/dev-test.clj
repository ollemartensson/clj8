(ns dev-test
  "Development and testing utilities for clj8."
  (:require [clojure.test :as test]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clj8.registry.registry :as reg]
            [clj8.client.client :as client]
            [clj8.macros.macros :as macros]
            [clj8.malli.malli :as malli]
            [malli.generator :as mg]))

;; Test discovery and execution
(defn find-test-namespaces
  "Discover test namespaces in the project."
  []
  (->> (file-seq (io/file "bricks"))
       (filter #(.isFile %))
       (map #(.getPath %))
       (filter #(str/includes? % "test"))
       (filter #(str/ends-with? % ".clj"))
       (map #(-> %
                 (str/replace #"bricks/[^/]+/test/" "")
                 (str/replace #"/" ".")
                 (str/replace #"\.clj$" "")
                 (str/replace #"_" "-")))
       (map symbol)
       (filter #(try (require %) true (catch Exception _ false)))))

(defn run-test-namespace
  "Run tests for a specific namespace."
  [ns-sym]
  (require ns-sym)
  (test/run-tests ns-sym))

(defn run-all-tests
  "Run all tests in the project."
  []
  (let [test-namespaces (find-test-namespaces)]
    (println "Running tests for namespaces:" test-namespaces)
    (apply test/run-tests test-namespaces)))

(defn run-generative-tests
  "Run property-based tests for clj8 components."
  []
  (println "Running generative tests...")

  ;; Test function name generation property
  (let [result (tc/quick-check 100
                               (prop/for-all [op-id gen/string-alphanumeric]
                                             (let [op-meta {:operation-id op-id}
                                                   fn-name (macros/generate-fn-name op-meta)]
                                               (and (symbol? fn-name)
                                                    (not (str/includes? (str fn-name) " "))))))]
    (println "Function name generation test:" (:pass? result)))

  ;; Test schema conversion roundtrip
  (let [result (tc/quick-check 50
                               (prop/for-all [schema-name gen/keyword]
                                             (let [ref-str (str "#/components/schemas/" (name schema-name))
                                                   extracted (macros/extract-schema-name ref-str)]
                                               (= schema-name extracted))))]
    (println "Schema name extraction test:" (:pass? result))))

;; Transducer helpers for K8s data processing
(defn xf-filter-by-kind
  "Transducer that filters K8s resources by kind."
  [kind]
  (filter #(= kind (get-in % [:metadata :kind]))))

(defn xf-extract-names
  "Transducer that extracts resource names."
  []
  (map #(get-in % [:metadata :name])))

(defn xf-group-by-namespace
  "Transducer that groups resources by namespace."
  []
  (group-by #(get-in % [:metadata :namespace])))

(defn xf-add-labels
  "Transducer that adds labels to resources."
  [labels]
  (map #(update-in % [:metadata :labels] merge labels)))

;; K8s API operation helpers
(defn list-available-operations
  "List all available K8s API operations."
  []
  (let [registry (reg/get-registry)]
    (->> registry
         (map (fn [[op-keyword op-meta]]
                {:operation op-keyword
                 :method (:method op-meta)
                 :path (:path op-meta)
                 :summary (:summary op-meta)}))
         (sort-by :operation))))

(defn find-operations-by-resource
  "Find operations related to a specific K8s resource type."
  [resource-type]
  (let [registry (reg/get-registry)
        resource-pattern (re-pattern (str "(?i)" resource-type))]
    (->> registry
         (filter (fn [[op-keyword op-meta]]
                   (or (re-find resource-pattern (str op-keyword))
                       (re-find resource-pattern (:summary op-meta "")))))
         (map first))))

(defn generate-sample-request
  "Generate sample request data for an operation using Malli."
  [op-keyword]
  (let [registry (reg/get-registry)
        op-meta (get registry op-keyword)
        spec (reg/load-openapi-spec)
        request-schema-ref (macros/get-body-param-schema-ref op-meta)]
    (when request-schema-ref
      (let [schema-name (macros/extract-schema-name request-schema-ref)
            malli-schema (malli/get-schema schema-name spec)]
        (when malli-schema
          (try
            (mg/generate malli-schema)
            (catch Exception e
              (println "Could not generate sample for" schema-name ":" (.getMessage e))
              nil)))))))

;; Development REPL helpers
(defn inspect-operation
  "Inspect a K8s API operation in detail."
  [op-keyword]
  (let [registry (reg/get-registry)
        op-meta (get registry op-keyword)
        spec (reg/load-openapi-spec)]
    (when op-meta
      (println "=== Operation:" op-keyword "===")
      (println "Summary:" (:summary op-meta))
      (println "Method:" (:method op-meta))
      (println "Path:" (:path op-meta))
      (println "Parameters:" (count (:parameters op-meta)))
      (when-let [req-schema (macros/get-body-param-schema-ref op-meta)]
        (println "Request schema:" (macros/extract-schema-name req-schema)))
      (when-let [resp-schema (macros/get-success-response-schema-ref op-meta)]
        (println "Response schema:" (macros/extract-schema-name resp-schema)))
      (println "Generated function name:" (macros/generate-fn-name op-meta)))))

(defn demo-operation
  "Demo an operation with sample data."
  [op-keyword]
  (println "=== Demo for" op-keyword "===")
  (inspect-operation op-keyword)
  (println)
  (when-let [sample (generate-sample-request op-keyword)]
    (println "Sample request data:")
    (clojure.pprint/pprint sample))
  (println))

;; Performance testing helpers
(defn benchmark-registry-generation
  "Benchmark registry generation performance."
  []
  (println "Benchmarking registry generation...")
  (let [start (System/nanoTime)
        registry (reg/generate-registry)
        duration (/ (- (System/nanoTime) start) 1000000.0)]
    (println "Registry generated in" duration "ms")
    (println "Operations count:" (count registry))
    {:duration-ms duration
     :operation-count (count registry)}))

(defn benchmark-schema-extraction
  "Benchmark Malli schema extraction performance."
  []
  (println "Benchmarking schema extraction...")
  (let [spec (reg/load-openapi-spec)
        start (System/nanoTime)
        schemas (malli/extract-schemas spec)
        duration (/ (- (System/nanoTime) start) 1000000.0)]
    (println "Schemas extracted in" duration "ms")
    (println "Schema count:" (count schemas))
    {:duration-ms duration
     :schema-count (count schemas)}))

;; Development workflow functions
(defn reload-all
  "Reload all clj8 namespaces for development."
  []
  (println "Reloading clj8 namespaces...")
  (require '[clj8.registry.registry :as reg] :reload)
  (require '[clj8.malli.malli :as malli] :reload)
  (require '[clj8.macros.macros :as macros] :reload)
  (require '[clj8.client.client :as client] :reload)
  (require '[clj8.core.core :as core] :reload)
  (println "All namespaces reloaded."))

(defn dev-status
  "Show development status and metrics."
  []
  (println "🎯 clj8 Development Status")
  (println "==========================")
  (let [registry (reg/get-registry)
        spec (reg/load-openapi-spec)
        schemas (malli/extract-schemas spec)]
    (println "Registry operations:" (count registry))
    (println "Malli schemas:" (count schemas))
    (println "Test namespaces:" (count (find-test-namespaces))))
  (println)
  (run-all-tests))

(comment
  ;; Development workflow examples

  ;; Reload and check status
  (reload-all)
  (dev-status)

  ;; Explore operations
  (list-available-operations)
  (find-operations-by-resource "pod")
  (inspect-operation :listNamespacedPod)
  (demo-operation :createNamespacedService)

  ;; Performance testing
  (benchmark-registry-generation)
  (benchmark-schema-extraction)

  ;; Generative testing
  (run-generative-tests)

  ;; Transducer examples with mock data
  (let [mock-pods [{:metadata {:name "pod1" :namespace "default" :kind "Pod"}}
                   {:metadata {:name "pod2" :namespace "kube-system" :kind "Pod"}}]]
    (->> mock-pods
         (transduce (comp (xf-filter-by-kind "Pod")
                          (xf-extract-names))
                    conj))))