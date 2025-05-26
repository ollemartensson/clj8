(ns clj8.client.client-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clj8.client.client :as client]
            [clj8.registry.registry :as reg]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]))

;; Access to private functions for testing
(def interpolate-path #'clj8.client.client/interpolate-path)
(def prepare-request-params #'clj8.client.client/prepare-request-params)

(deftest interpolate-path-test
  (testing "Interpolates path parameters correctly"
    (let [path-template "/api/v1/namespaces/{namespace}/pods/{name}"
          params {:namespace "default" :name "my-pod"}
          expected "/api/v1/namespaces/default/pods/my-pod"]
      (is (= expected (interpolate-path path-template params))))

    (testing "Handles multiple occurrences of same parameter"
      (let [path-template "/api/v1/namespaces/{name}/pods/{name}"
            params {:name "test"}
            expected "/api/v1/namespaces/test/pods/test"]
        (is (= expected (interpolate-path path-template params)))))

    (testing "Leaves unmatched parameters untouched"
      (let [path-template "/api/v1/namespaces/{namespace}/pods/{name}"
            params {:namespace "default"}
            expected "/api/v1/namespaces/default/pods/{name}"]
        (is (= expected (interpolate-path path-template params)))))))

(deftest prepare-request-params-test
  (testing "Separates parameters by location"
    (let [op-params {:parameters [{"name" "namespace" "in" "path" "required" true}
                                  {"name" "pretty" "in" "query"}
                                  {"name" "Authorization" "in" "header"}]}
          provided-params {:namespace "default"
                           :pretty true
                           :Authorization "Bearer token123"
                           :body {:some "data"}}
          result (prepare-request-params op-params provided-params)]

      (is (= "default" (get-in result [:path :namespace]))
          "Path parameters should be extracted")
      (is (= true (get-in result [:query :pretty]))
          "Query parameters should be extracted")
      (is (= "Bearer token123" (get-in result [:headers "Authorization"]))
          "Header parameters should be extracted")
      (is (= {:some "data"} (:body result))
          "Body parameter should be included")))

  (testing "Handles missing parameters gracefully"
    (let [op-params {:parameters [{"name" "namespace" "in" "path" "required" true}
                                  {"name" "pretty" "in" "query"}]}
          provided-params {:namespace "default"}
          result (prepare-request-params op-params provided-params)]

      (is (= "default" (get-in result [:path :namespace])))
      (is (not (contains? (:query result) :pretty))))))

(deftest invoke-test
  (testing "Makes correct API calls"
    ;; Create a mock version of http/request that we'll use for testing
    (let [last-request-atom (atom nil)
          mock-response {:status 200
                         :body (json/encode {:kind "PodList" :items []})}
          mock-registry (atom {:listNamespacedPod
                               {:method :get
                                :path "/api/v1/namespaces/{namespace}/pods"
                                :operation-id "listNamespacedPod"
                                :summary "List all pods in namespace"
                                :parameters [{"name" "namespace" "in" "path" "required" true}
                                             {"name" "pretty" "in" "query"}]}})]

      ;; Replace the real functions with mock versions
      (with-redefs [http/request (fn [request-map]
                                   (reset! last-request-atom request-map)
                                   mock-response)
                    reg/lookup (fn [op-keyword]
                                 (get @mock-registry (keyword (name op-keyword))))]

        (let [result (client/invoke :listNamespacedPod
                                    {:namespace "default"
                                     :pretty true
                                     :kube-api-server "https://k8s.example.com"})]

          ;; Verify the request was constructed correctly
          (is (some? @last-request-atom) "HTTP request should have been made")
          (is (= :get (:method @last-request-atom)) "Method should be :get")
          (is (= "https://k8s.example.com/api/v1/namespaces/default/pods"
                 (:url @last-request-atom))
              "URL should include base URL and interpolated path")
          (is (= {:pretty true} (:query-params @last-request-atom))
              "Query parameters should be included")
          (is (map? result) "Result should be parsed JSON"))))))

(deftest invoke-error-test
  (testing "Handles error responses correctly"
    ;; Create a mock version of http/request that returns an error
    (let [mock-error-response {:status 404
                               :body (json/encode {:kind "Status"
                                                   :message "Pod not found"
                                                   :reason "NotFound"
                                                   :status "Failure"})}
          mock-registry (atom {:readNamespacedPod
                               {:method :get
                                :path "/api/v1/namespaces/{namespace}/pods/{name}"
                                :operation-id "readNamespacedPod"
                                :summary "Read pod in namespace"
                                :parameters [{"name" "namespace" "in" "path" "required" true}
                                             {"name" "name" "in" "path" "required" true}]}})]

      ;; Replace the real functions with mock versions
      (with-redefs [http/request (fn [request-map] mock-error-response)
                    reg/lookup (fn [op-keyword]
                                 (get @mock-registry (keyword (name op-keyword))))]

        ;; The invoke function should throw an exception for 4xx responses
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Request execution failed"
                              (client/invoke :readNamespacedPod
                                             {:namespace "default"
                                              :name "nonexistent-pod"}))
            "Should throw an exception for 404 responses")))))
