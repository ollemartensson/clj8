(ns clj8.registry.registry-test
  (:require [clojure.test :refer :all]
            [clj8.registry.registry :as reg]))

(deftest openapi-spec-loading-test
  (testing "Loads and parses the OpenAPI spec without errors."
    (let [spec (reg/load-openapi-spec)]
      (is (some? spec) "Spec should not be nil")
      (is (map? spec) "Spec should be a map")
      (is (pos? (count (keys spec))) "Spec should have top-level keys"))))

(deftest extract-endpoints-test
  (testing "Extracts endpoints from a sample OpenAPI spec."
    (let [sample-spec {:paths {"/api/v1/pods" {:get {:operationId "listPods"
                                                     :summary "List all pods"
                                                     :description "A longer description for listing pods."
                                                     :parameters [{:name "namespace" :in "query"}]
                                                     :responses {:200 {:description "OK"}}}}}
                       :components {:schemas {:Pod {:type "object" :properties {:name {:type "string"}}}}}}
          endpoints (reg/extract-endpoints sample-spec)]
      (is (seq endpoints) "Should extract at least one endpoint.")
      (let [endpoint (first endpoints)]
        (is (= :get (:method endpoint)) "Method should be :get")
        (is (= "/api/v1/pods" (:path endpoint)) "Path should be correct")
        (is (= "listPods" (:operation-id endpoint)) "Operation ID should be correct")
        (is (= "List all pods" (:summary endpoint)) "Summary should be correct")
        (is (some? (:parameters endpoint)))
        (is (some? (:responses endpoint)))))))

(deftest generate-registry-test
  (testing "Generates a non-empty registry from the OpenAPI spec."
    (let [registry (reg/generate-registry)]
      (is (map? registry) "Registry should be a map.")
      (is (pos? (count registry)) "Registry should not be empty.")))

(deftest generate-registry-with-sample-spec-test
  (testing "Generates a registry from a sample OpenAPI spec."
    (let [sample-spec {:paths {"/api/v1/pods" {:get {:operationId "listPods"
                                                     :summary "List all pods"
                                                     :description "A longer description for listing pods."}}}}
          registry (reg/generate-registry sample-spec)]
      (is (map? registry) "Registry should be a map.")
      (is (= 1 (count registry)) "Registry should contain one entry.")
      (let [op-key (keyword "listPods")
            entry (get registry op-key)]
        (is (some? entry) (str "Registry should contain key: " op-key))
        (is (= :get (:method entry)))
        (is (= "/api/v1/pods" (:path entry)))
        (is (= "listPods" (:operation-id entry)))))))

(deftest lookup-test
  (testing "Looks up an operation from the generated registry."
    (let [registry (reg/generate-registry) ; Uses the actual spec
          ;; Find a known operationId from your actual spec for a more robust test
          ;; For now, let's assume one might be generated like this if spec is loaded:
          ;; This part will likely fail until generate-registry works with the real spec.
          ;; You'll need to inspect a successfully generated registry to pick a real op-id.
          example-op-id (-> registry keys first)]
      (if example-op-id
        (let [op-meta (reg/lookup example-op-id)]
          (is (some? op-meta) (str "Metadata should exist for operation: " example-op-id))
          (is (= example-op-id (keyword (:operation-id op-meta)))))
        (println "Skipping lookup-test details as registry is empty or op-id couldn't be determined")))))

; To run tests, you might need to ensure your test alias in deps.edn is set up
; and then use a command like:
; clojure -X:test
; or run them from your editor/REPL.