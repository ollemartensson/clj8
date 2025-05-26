(ns clj8.core.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [clj8.core.core :as core]
            [clj8.registry.registry :as registry]
            [clj8.client.client :as client]))

(deftest connect-test
  (testing "connect! creates a valid connection context"
    (let [connection (core/connect! {:server "https://example.com"
                                     :token "my-token"
                                     :insecure? true
                                     :context-name "my-cluster"})]
      (is (= "https://example.com" (:server connection)))
      (is (= "my-token" (:token connection)))
      (is (= true (:insecure? connection)))
      (is (= "my-cluster" (:context-name connection)))

      (is (= connection (core/current-connection)) "Current connection should match created connection"))))

(deftest simple-mock-test
  (testing "Simple mocked test to verify mocking works"
    (with-redefs [registry/find-operation-by-kind (fn [op-type kind]
                                                    (keyword (str (name op-type) "-" (str/lower-case kind))))
                  client/invoke (fn [op-keyword params]
                                  {:op op-keyword :params params :result :mock-response})]
      ;; Simple test case
      (let [result (client/invoke :test-op {:name "test"})]
        (is (= :test-op (:op result)))
        (is (= {:name "test"} (:params result)))
        (is (= :mock-response (:result result)))))))
