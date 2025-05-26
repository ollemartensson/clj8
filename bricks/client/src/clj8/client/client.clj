(ns clj8.client.client
  "Platform-agnostic HTTP client for Kubernetes API operations."
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clj8.registry.registry :as reg]))

(defn interpolate-path
  "Interpolates path parameters in a URL path.
  Example: '/api/v1/namespaces/{namespace}/pods' with {:namespace 'default'}
  becomes '/api/v1/namespaces/default/pods'"
  [path params]
  (reduce (fn [result [param-key param-value]]
            (str/replace result
                         (str "{" (name param-key) "}")
                         (str param-value)))
          path
          params))

(defn categorize-params
  "Separates provided params into :path, :query, and :body based on OpenAPI parameter definitions."
  [op-details provided-params]
  (let [param-defs (or (:parameters op-details) [])
        categorized (reduce
                     (fn [acc p-def]
                       (let [param-name (keyword (or (get p-def "name") (get p-def :name)))
                             param-in (or (get p-def "in") (get p-def :in))]
                         (if-let [param-value (get provided-params param-name)]
                           (case param-in
                             "path" (assoc-in acc [:path param-name] param-value)
                             "query" (assoc-in acc [:query param-name] param-value)
                             "header" (assoc-in acc [:headers param-name] param-value)
                             acc)
                           acc)))
                     {:path {} :query {} :headers {}}
                     param-defs)]
    (if (and (not (:body categorized)) (:body provided-params))
      (assoc categorized :body (:body provided-params))
      categorized)))

(defn prepare-request-params
  "Prepares request parameters by categorizing them."
  [op-details provided-params]
  (categorize-params op-details provided-params))

;; Middleware system for request/response processing
(defprotocol Middleware
  "Protocol for HTTP middleware components."
  (wrap-request [this request] "Transform the request")
  (wrap-response [this response] "Transform the response"))

(defrecord AuthMiddleware [token]
  Middleware
  (wrap-request [_ request]
    (if token
      (assoc-in request [:headers "Authorization"] (str "Bearer " token))
      request))
  (wrap-response [_ response] response))

(defrecord LoggingMiddleware [enabled?]
  Middleware
  (wrap-request [_ request]
    (when enabled?
      (println "Request:" (:method request) (:url request)))
    request)
  (wrap-response [_ response]
    (when enabled?
      (println "Response:" (:status response)))
    response))

(defrecord ValidationMiddleware [validate-request? validate-response?]
  Middleware
  (wrap-request [_ request]
    ;; TODO: Add Malli request validation
    request)
  (wrap-response [_ response]
    ;; TODO: Add Malli response validation  
    response))

(defn apply-middleware
  "Apply middleware chain to request and response."
  [middleware-chain request response-fn]
  (let [processed-request (reduce #(wrap-request %2 %1) request middleware-chain)
        response (response-fn processed-request)]
    (reduce #(wrap-response %2 %1) response (reverse middleware-chain))))

(defn create-default-middleware
  "Create default middleware chain for K8s API calls."
  [options]
  (cond-> []
    (:kube-token options) (conj (->AuthMiddleware (:kube-token options)))
    (:log-requests? options) (conj (->LoggingMiddleware true))
    (:validate? options) (conj (->ValidationMiddleware true true))))

(defn execute-http-request
  "Execute HTTP request with error handling."
  [request-map]
  (try
    (let [response (http/request request-map)]
      (cond
        (>= (:status response) 400)
        (throw (ex-info "Request execution failed"
                        {:status (:status response)
                         :body (:body response)
                         :request request-map}
                        (Exception. (str "API Error: " (:status response)
                                         " - " (get-in response [:body :message] "Unknown error")))))
        :else response))
    (catch Exception e
      (if (instance? clojure.lang.ExceptionInfo e)
        (throw e)
        (throw (ex-info "Request execution failed"
                        {:request request-map} e))))))

(defn invoke
  "Invokes a Kubernetes API operation with middleware support.
  
  Args:
    op-keyword: The operation keyword (e.g., :listNamespacedPod)
    params: A map of parameters for the operation
    
  Options in params:
    :kube-api-server - Override API server URL
    :kube-token - Authentication token
    :log-requests? - Enable request logging
    :validate? - Enable request/response validation
    :middleware - Custom middleware chain
    
  Returns:
    Response map with parsed JSON body"
  [op-keyword params]
  (let [op-details (reg/lookup op-keyword)]
    (if-not op-details
      (throw (ex-info (str "Operation not found: " op-keyword) {:op op-keyword}))
      (let [{:keys [method path summary]} op-details
            prepared-params (prepare-request-params op-details params)
            interpolated-path (interpolate-path path (:path prepared-params))
            http-method (if (keyword? method)
                          method
                          (clojure.core/keyword (str/lower-case (name method))))
            base-url (or (:kube-api-server params)
                         (System/getenv "KUBE_API_SERVER")
                         "http://localhost:8080")
            request-map (cond-> {:method http-method
                                 :url (str base-url interpolated-path)
                                 :throw-exceptions false
                                 :content-type :json
                                 :accept :json
                                 :as :json}
                          (seq (:query prepared-params)) (assoc :query-params (:query prepared-params))
                          (seq (:headers prepared-params)) (update :headers merge (:headers prepared-params))
                          (:body prepared-params) (assoc :body (json/generate-string (:body prepared-params))))

            middleware-chain (or (:middleware params)
                                 (create-default-middleware params))]

        (apply-middleware middleware-chain
                          request-map
                          execute-http-request)))))

;; Connection management for easier API server configuration
(def ^:dynamic *current-connection* nil)

(defn connect!
  "Create a connection context for K8s API calls.
  
  Args:
    config: Map with :server, :token, :insecure?, :context-name, etc.
    
  Returns:
    Connection context map"
  [config]
  (let [connection (merge {:server "http://localhost:8080"
                           :insecure? false
                           :timeout 30000}
                          config)]
    (alter-var-root #'*current-connection* (constantly connection))
    connection))

(defn current-connection
  "Get the current connection context."
  []
  *current-connection*)

(defn with-connection
  "Execute operations with a specific connection context."
  [connection f]
  (binding [*current-connection* connection]
    (f)))