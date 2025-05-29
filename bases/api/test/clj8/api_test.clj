(ns clj8.api-test
  (:require [clojure.test :refer [deftest is testing]]
            [clj8.api :as api]))

(deftest configuration-test
  (testing "Configuration management"
    (let [config {:server "https://test.com" :namespace "test" :timeout 5000}]
      (api/configure! config)
      (is (= "test" (:namespace (api/current-config))))
      (is (= 5000 (:timeout (api/current-config))))

      ;; Clean up
      (api/close!)
      (is (= {} (api/current-config))))))

(deftest version-test
  (testing "Version information"
    (let [version-info (api/version)]
      (is (string? (:version version-info)))
      (is (number? (:operations version-info)))
      (is (number? (:schemas version-info))))))

(deftest operation-listing-test
  (testing "Operation listing and description"
    (let [operations (api/list-operations)]
      (is (vector? operations))
      (is (> (count operations) 0))
      (is (every? :operation operations))
      (is (every? :method operations))
      (is (every? :path operations)))

    (testing "operation description"
      (let [op-key (first (keys (require '[clj8.registry.registry :as reg]) (reg/get-registry)))
            description (api/describe-operation op-key)]
        (is (some? description))
        (is (contains? description :method))
        (is (contains? description :path))))))

(deftest utility-functions-test
  (testing "Utility functions"
    (testing "healthy? function"
      (let [healthy-pod {:status {:phase "Running"
                                  :containerStatuses [{:ready true}]}}
            unhealthy-pod {:status {:phase "Failed"}}]
        (is (api/healthy? healthy-pod))
        (is (not (api/healthy? unhealthy-pod)))))

    (testing "merge-config-params"
      (api/configure! {:namespace "default" :timeout 1000})
      ;; This would test the private function if exposed
      )))