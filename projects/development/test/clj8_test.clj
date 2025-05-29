(ns clj8-test
  "Main test namespace for clj8 Polylith integration."
  (:require [clojure.test :refer [deftest is testing]]
            [dev-test :as dt]))

(deftest clj8-integration-test
  (testing "clj8 comprehensive test suite"
    (println "🧪 Running clj8 test suite...")
    (let [results (dt/run-all-tests)]
      (println "Test results:" results)
      (is (map? results) "Should return test results map")
      (is (> (:total results 0) 0) "Should run tests")
      (is (= (:failures results 0) 0) "Should have no failures")
      (is (= (:errors results 0) 0) "Should have no errors"))))

(deftest simple-smoke-test
  (testing "Basic functionality smoke test"
    (is (= 2 (+ 1 1)) "Basic arithmetic")
    (is (string? "test") "Basic types")
    (require '[clj8.registry.registry :as reg])
    (is (fn? reg/get-registry) "Registry functions available")
    (require '[clj8.api :as api])
    (is (fn? api/configure!) "API functions available")))