(ns all-tests
  "Comprehensive test runner for all clj8 components."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [dev-test :as dt]
            ;; Explicitly require all test namespaces
            [clj8.registry.registry-test]
            [clj8.malli.malli-test]
            [clj8.client.client-test]
            [clj8.macros.macros-test]
            [clj8.core.core-test]
            [clj8.api-test]
            [development-test]))

(deftest comprehensive-test-suite
  (testing "All clj8 components have passing tests"
    (let [results (dt/run-all-tests)]
      (is (> (:total results) 50) "Should run comprehensive test suite")
      (is (= 0 (:failures results)) "All tests should pass")
      (is (= 0 (:errors results)) "No test errors"))))

(deftest individual-brick-tests
  (testing "Individual brick test execution"
    (testing "Registry tests"
      (let [results (run-tests 'clj8.registry.registry-test)]
        (is (= 0 (:fail results 0)) "Registry tests pass")))

    (testing "Malli tests"
      (let [results (run-tests 'clj8.malli.malli-test)]
        (is (= 0 (:fail results 0)) "Malli tests pass")))

    (testing "Client tests"
      (let [results (run-tests 'clj8.client.client-test)]
        (is (= 0 (:fail results 0)) "Client tests pass")))

    (testing "Macros tests"
      (let [results (run-tests 'clj8.macros.macros-test)]
        (is (= 0 (:fail results 0)) "Macros tests pass")))

    (testing "API tests"
      (let [results (run-tests 'clj8.api-test)]
        (is (= 0 (:fail results 0)) "API tests pass")))))

(defn -main
  "Entry point for running all tests."
  [& args]
  (println "🧪 Running comprehensive clj8 test suite...")
  (let [results (run-tests 'all-tests)]
    (if (and (= 0 (:fail results 0))
             (= 0 (:error results 0)))
      (do
        (println "✅ All tests passed!")
        (System/exit 0))
      (do
        (println "❌ Some tests failed")
        (System/exit 1)))))