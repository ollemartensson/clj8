(ns clj8.client.client
  (:require [clj8.registry.registry :as reg]
            [clojure.string :as str]
            [clj-http.client :as http]
            [cheshire.core :as json]))

(defn- interpolate-path
  "Replaces path parameters in a path template string.
  Example: For path template \"/api/v1/namespaces/{namespace}/pods/{name}\" and params {:namespace \"default\" :name \"my-pod\"}
  Returns: \"/api/v1/namespaces/default/pods/my-pod\""
  [path-template path-params]
  (reduce-kv
   (fn [path key value]
     (str/replace path (str "{" (name key) "}") (str value)))
   path-template
   path-params))

(defn- prepare-request-params
  "Separates provided params into :path, :query, and :body based on OpenAPI parameter definitions."
  [op-params provided-params]
  (let [param-defs (:parameters op-params [])
        categorized (reduce
                     (fn [acc p-def]
                       (let [param-name (keyword (get p-def "name"))
                             param-in (get p-def "in")
                             value (get provided-params param-name)]
                         (if (some? value)
                           (case param-in
                             "path" (assoc-in acc [:path param-name] value)
                             "query" (assoc-in acc [:query param-name] value)
                             "body" (assoc acc :body value)
                             "header" (assoc-in acc [:headers (get p-def "name")] value)
                             acc)
                           acc)))
                     {:path {} :query {} :headers {}}
                     param-defs)]
    (if (and (not (:body categorized)) (:body provided-params))
      (assoc categorized :body (:body provided-params))
      categorized)))

(defn invoke
  "Invokes a Kubernetes API operation.
  - op-keyword: The operation keyword (e.g., :core-v1-list-pod-for-all-namespaces).
  - params: A map of parameters for the operation."
  [op-keyword params]
  (let [op-details (reg/lookup op-keyword)]
    (if-not op-details
      (throw (ex-info (str "Operation not found: " op-keyword) {:op op-keyword}))
      (let [{:keys [method path summary]} op-details
            prepared-params (prepare-request-params op-details params)
            interpolated-path (interpolate-path path (:path prepared-params))
            http-method (keyword (str/lower-case method))
            base-url (or (:kube-api-server params) (System/getenv "KUBE_API_SERVER") "http://localhost:8080")
            auth-token (or (:kube-token params) (System/getenv "KUBE_TOKEN"))
            request-map (cond-> {:method http-method
                                 :url (str base-url interpolated-path)
                                 :throw-exceptions false
                                 :content-type :json
                                 :as :text}
                          (seq (:query prepared-params)) (assoc :query-params (:query prepared-params))
                          (:body prepared-params) (assoc :body (json/encode (:body prepared-params)))
                          auth-token (assoc :headers (merge {"Authorization" (str "Bearer " auth-token)}
                                                            (:headers prepared-params))))]
        (println (str "Invoking: " summary " (" op-keyword ")"))
        (println (str "Request: " (name http-method) " " (:url request-map)))
        (try
          (let [response (http/request request-map)
                response-body (if (not-empty (:body response))
                                (try (json/parse-string (:body response) true)
                                     (catch Exception _e ; Use _e to indicate unused binding
                                       (println "Failed to parse JSON response body:" (:body response))
                                       {:error :json-parse-failed :raw-body (:body response)}))
                                nil)]
            (if (>= (:status response) 400)
              (do
                (println (str "Error response [" (:status response) "]: " response-body))
                (throw (ex-info (str "API Error: " (:status response) " - " (or (:message response-body) summary))
                                {:op op-keyword :status (:status response) :response response-body})))
              response-body))
          (catch Exception e
            (println (str "HTTP request failed for " op-keyword ": " (.getMessage e)))
            (throw (ex-info (str "Request execution failed for " op-keyword)
                            {:op op-keyword :params params} e))))))))

(comment
  ;; (reg/get-registry) ; Ensure registry is loaded
  ;; (invoke :corev1listNamespacedPod {:namespace "default"})
  )