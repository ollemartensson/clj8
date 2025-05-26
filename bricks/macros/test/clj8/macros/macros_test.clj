(ns clj8.macros.macros-test
  (:require [clojure.test :refer [deftest is testing]]
            [clj8.macros.macros :as macros]
            [clj8.registry.registry :as reg]))

;; Access to private functions for testing
(def format-param-for-doc #'clj8.macros.macros/format-param-for-doc)
(def generate-fn-name #'clj8.macros.macros/generate-fn-name)
(def get-body-param-schema-ref #'clj8.macros.macros/get-body-param-schema-ref)
(def get-success-response-schema-ref #'clj8.macros.macros/get-success-response-schema-ref)
(def extract-schema-name #'clj8.macros.macros/extract-schema-name)
(def generate-function-metadata #'clj8.macros.macros/generate-function-metadata)

(deftest format-param-for-doc-test
  (testing "Formats parameter definitions for documentation"
    (let [basic-param {"name" "namespace"
                       "in" "path"
                       "required" true
                       "schema" {"type" "string"}}
          formatted (format-param-for-doc basic-param)]
      (is (string? formatted))
      (is (.contains formatted "namespace"))
      (is (.contains formatted "path"))
      (is (.contains formatted "[required]"))
      (is (.contains formatted "string")))

    (testing "with description"
      (let [param-with-desc {"name" "pretty"
                             "in" "query"
                             "description" "Pretty-print the output"}
            formatted (format-param-for-doc param-with-desc)]
        (is (.contains formatted "Pretty-print the output"))))))

(deftest generate-fn-name-test
  (testing "Generates function names from operation metadata"
    (testing "from operation-id"
      (let [op-meta {:operation-id "listNamespacedPod"}
            fn-name (generate-fn-name op-meta)]
        (is (= 'list-namespaced-pod fn-name))))

    (testing "from camelCase operation-id"
      (let [op-meta {:operation-id "createNamespacedService"}
            fn-name (generate-fn-name op-meta)]
        (is (= 'create-namespaced-service fn-name))))

    (testing "fallback to path when no operation-id"
      (let [op-meta {:path "/api/v1/namespaces/{namespace}/pods"}
            fn-name (generate-fn-name op-meta)]
        (is (symbol? fn-name))
        (is (not (str/includes? (str fn-name) "/")))))

    (testing "handles special characters"
      (let [op-meta {:operation-id "list-pods_v1"}
            fn-name (generate-fn-name op-meta)]
        (is (= 'list-pods-v1 fn-name))))))

(deftest get-body-param-schema-ref-test
  (testing "Extracts schema reference from request body"
    (let [op-meta {:schemas {:request {"$ref" "#/components/schemas/Pod"}}}
          schema-ref (get-body-param-schema-ref op-meta)]
      (is (= "#/components/schemas/Pod" schema-ref)))

    (testing "handles missing request schema"
      (let [op-meta {:schemas {}}
            schema-ref (get-body-param-schema-ref op-meta)]
        (is (nil? schema-ref))))))

(deftest get-success-response-schema-ref-test
  (testing "Extracts schema reference from success response"
    (let [op-meta {:schemas {:response {"$ref" "#/components/schemas/PodList"}}}
          schema-ref (get-success-response-schema-ref op-meta)]
      (is (= "#/components/schemas/PodList" schema-ref)))

    (testing "handles no response schema"
      (let [op-meta {:schemas {}}
            schema-ref (get-success-response-schema-ref op-meta)]
        (is (nil? schema-ref))))))

(deftest extract-schema-name-test
  (testing "Extracts schema name from $ref"
    (let [ref "#/components/schemas/io.k8s.api.core.v1.Pod"
          schema-name (extract-schema-name ref)]
      (is (= :io.k8s.api.core.v1.Pod schema-name)))

    (testing "handles nil ref"
      (let [schema-name (extract-schema-name nil)]
        (is (nil? schema-name))))))

(deftest generate-function-metadata-test
  (testing "Generates rich metadata for K8s API functions"
    (let [op-meta {:operation-id "listNamespacedPod"
                   :summary "List pods in a namespace"
                   :method :get
                   :path "/api/v1/namespaces/{namespace}/pods"
                   :parameters [{"name" "namespace" "in" "path" "required" true}]
                   :schemas {:response {"$ref" "#/components/schemas/PodList"}}}
          spec {}
          metadata (generate-function-metadata op-meta spec)]

      (is (string? (:doc metadata)))
      (is (.contains (:doc metadata) "List pods in a namespace"))
      (is (.contains (:doc metadata) "GET"))
      (is (.contains (:doc metadata) "/api/v1/namespaces/{namespace}/pods"))
      (is (.contains (:doc metadata) "Parameters:"))
      (is (.contains (:doc metadata) "Response schema: PodList"))

      (is (= '([params] [params options]) (:arglists metadata)))
      (is (= "listNamespacedPod" (:clj8/operation-id metadata)))
      (is (= :get (:clj8/method metadata)))
      (is (= "/api/v1/namespaces/{namespace}/pods" (:clj8/path metadata)))
      (is (= :PodList (:clj8/response-schema metadata))))))

(deftest defk8sapi-fn-test
  (testing "defk8sapi-fn macro generates valid code"
    ;; Mock the registry to have a test operation
    (with-redefs [reg/get-registry (fn [] {:testOp {:operation-id "testOp"
                                                    :summary "Test operation"
                                                    :method :get
                                                    :path "/test"}})
                  reg/load-openapi-spec (fn [] {})]
      (let [expanded (macroexpand-1 '(clj8.macros.macros/defk8sapi-fn :testOp))]
        (is (seq? expanded))
        (is (= 'defn (first expanded)))
        (is (= 'test-op (second expanded)))))))

(deftest defk8sapi-fns-test
  (testing "defk8sapi-fns macro generates valid code"
    ;; Mock the registry function to return our test data
    (with-redefs [reg/get-registry (fn [] {:listNamespacedPod
                                           {:operation-id "listNamespacedPod"
                                            :method :get
                                            :path "/api/v1/namespaces/{namespace}/pods"
                                            :summary "List pods in a namespace"
                                            :parameters [{"name" "namespace" "in" "path" "required" true
                                                          "schema" {"type" "string"}}]}})
                  reg/load-openapi-spec (fn [] {})]
      (let [expanded (macroexpand-1 '(clj8.macros.macros/defk8sapi-fns))]
        ;; Basic structure validation
        (is (= 'do (first expanded)) "Should expand to a do form")
        (is (>= (count expanded) 2) "Should contain at least one definition")))))
