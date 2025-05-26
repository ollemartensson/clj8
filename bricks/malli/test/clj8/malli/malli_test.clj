(ns clj8.malli.malli-test
  "Tests for OpenAPI to Malli schema conversion."
  (:require [clojure.test :refer [deftest is testing]]
            [clj8.malli.malli :as malli]
            [clj8.registry.registry :as reg]
            [malli.core :as m]))

(deftest openapi-type->malli-test
  (testing "Converts basic OpenAPI types to Malli schemas"
    (is (= :string (malli/openapi-type->malli {"type" "string"})))
    (is (= :int (malli/openapi-type->malli {"type" "integer" "format" "int32"})))
    (is (= :int (malli/openapi-type->malli {"type" "integer" "format" "int64"})))
    (is (= :double (malli/openapi-type->malli {"type" "number" "format" "double"})))
    (is (= :boolean (malli/openapi-type->malli {"type" "boolean"})))

    (testing "array types"
      (is (= [:vector :string]
             (malli/openapi-type->malli {"type" "array" "items" {"type" "string"}}))))

    (testing "enum types"
      (is (= [:enum :pending :running :succeeded :failed]
             (malli/openapi-type->malli {"type" "string"
                                         "enum" ["pending" "running" "succeeded" "failed"]}))))

    (testing "object types"
      (let [result (malli/openapi-type->malli {"type" "object"
                                               "properties" {"name" {"type" "string"}
                                                             "count" {"type" "integer"}}
                                               "required" ["name"]})]
        (is (vector? result))
        (is (= :map (first result)))))))

(deftest convert-object-properties-test
  (testing "Converts object properties with required/optional fields"
    (let [properties {"name" {"type" "string"}
                      "age" {"type" "integer"}
                      "email" {"type" "string"}}
          type-def {"required" ["name" "age"]}
          result (malli/convert-object-properties properties type-def)]
      (is (= 3 (count result)))
      ;; Check that we have the right number of properties
      (is (every? vector? result))
      (is (every? keyword? (map first result))))))

(deftest resolve-ref-test
  (testing "Resolves OpenAPI $ref paths"
    (let [spec {"components" {"schemas" {"Pod" {"type" "object"
                                                "properties" {"name" {"type" "string"}}}}}}
          resolved (malli/resolve-ref "#/components/schemas/Pod" spec)]
      (is (= {"type" "object" "properties" {"name" {"type" "string"}}} resolved)))))

(deftest convert-schema-test
  (testing "Converts OpenAPI schema with $ref resolution"
    (let [spec {"components" {"schemas" {"Pod" {"type" "object"
                                                "properties" {"name" {"type" "string"}}}}}}
          schema-with-ref {"$ref" "#/components/schemas/Pod"}
          result (malli/convert-schema schema-with-ref spec)]
      (is (vector? result))
      (is (= :map (first result)))))

  (deftest extract-schemas-test
    (testing "Extracts and converts schemas from OpenAPI spec"
      (let [spec {"components" {"schemas" {"Pod" {"type" "object"
                                                  "properties" {"name" {"type" "string"}}}
                                           "Service" {"type" "object"
                                                      "properties" {"port" {"type" "integer"}}}}}}
            schemas (malli/extract-schemas spec)]
        (is (= 2 (count schemas)))
        (is (contains? schemas :Pod))
        (is (contains? schemas :Service)))))

  (deftest validate-with-schema-test
    (testing "Validates data against Malli schema"
      (let [schema [:map [:name :string] [:age {:optional true} :int]]
            valid-data {:name "Alice" :age 30}
            invalid-data {:name 123}]

        (testing "valid data"
          (let [result (malli/validate-with-schema schema valid-data)]
            (is (:valid? result))
            (is (= valid-data (:data result)))))

        (testing "invalid data"
          (let [result (malli/validate-with-schema schema invalid-data)]
            (is (not (:valid? result)))
            (is (some? (:errors result))))))))

  (deftest generate-sample-test
    (testing "Generates sample data from Malli schema"
      (let [schema [:map [:name :string] [:age :int]]
            sample (malli/generate-sample schema)]
        (is (map? sample))
        (is (contains? sample :name))
        (is (contains? sample :age))
        (is (string? (:name sample)))
        (is (int? (:age sample))))))

  (deftest schema-registry-test
    (testing "Schema registry caching"
      (malli/clear-registry!)

      (testing "register and retrieve schema"
        (let [schema [:map [:name :string]]
              registered (malli/register-schema! :test-schema schema)]
          (is (= schema registered))
          (is (= schema (malli/get-schema :test-schema)))))

      (malli/clear-registry!)))

  (deftest k8s-schema-integration-test
    (testing "Integration with K8s schemas"
      (let [spec (reg/load-openapi-spec)
            schemas (malli/extract-schemas spec)]
        (is (> (count schemas) 50) "Should extract many K8s schemas")
        (is (some? (get schemas :io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta))
            "Should extract common K8s schemas"))))
