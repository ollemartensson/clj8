(ns development-test
  "Development project tests for Polylith."
  (:require [clojure.test :refer [deftest is testing]]
            [dev-test :as dt]))

(deftest development-project-test
  (testing "Development project can run all clj8 tests"
    (let [results (dt/run-all-tests)]
      (is (> (:total results) 50) "Should run many tests")
      (is (= 0 (:failures results)) "Should have no failures")
      (is (= 0 (:errors results)) "Should have no errors"))))

(deftest smoke-test
  (testing "Basic smoke test for Polylith"
    (is (= 4 (+ 2 2)) "Basic arithmetic works")
    (is (string? "hello") "Basic type checks work")))