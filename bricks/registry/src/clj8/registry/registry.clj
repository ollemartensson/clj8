(ns clj8.registry.registry
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def openapi-path "resources/k8s-openapi.json")

(defn load-openapi-spec
  "Loads and parses the Kubernetes OpenAPI spec as EDN (from JSON)."
  ([] (load-openapi-spec openapi-path))
  ([path]
   (println (str "Attempting to load OpenAPI spec from: " path " (resolved: " (.getAbsolutePath (io/file path)) ")"))
   (if (.exists (io/file path))
     (with-open [rdr (io/reader path)]
       (let [spec (json/parse-stream rdr true)]
         (println (str "Successfully parsed OpenAPI spec. Top-level keys count: " (count (keys spec))))
         spec))
     (do
       (println (str "!!! OpenAPI spec file NOT FOUND at: " path " (resolved: " (.getAbsolutePath (io/file path)) ")"))
       nil))))

(defn extract-endpoints
  "Extracts endpoints from the OpenAPI spec. Returns a sequence of maps with method, path, operationId, and docstring."
  [openapi]
  (println "DEBUG: extract-endpoints [openapi] ENTER. Spec nil?" (nil? openapi))
  (let [result (if openapi
                 (do
                   (println "DEBUG: extract-endpoints - openapi keys:" (keys openapi))
                   (println "DEBUG: extract-endpoints - :paths key is map?" (map? (:paths openapi)))
                   (println "DEBUG: extract-endpoints - BEFORE for-loop")
                   (let [endpoints (if (map? (:paths openapi))
                                     (for [[path methods] (:paths openapi)
                                           [method meta] methods]
                                       {:method method
                                        :path path
                                        :operation-id (:operationId meta)
                                        :summary (:summary meta)
                                        :description (:description meta)
                                        :parameters (:parameters meta)
                                        :responses (:responses meta)})
                                     [])]
                     (println "DEBUG: extract-endpoints - AFTER for-loop. Endpoints count:" (count endpoints))
                     endpoints))
                 (do
                   (println "Skipping endpoint extraction because OpenAPI spec is nil.")
                   []))]
    (println "DEBUG: extract-endpoints [openapi] EXIT")
    result))

(defn generate-registry
  "Generates a registry map of operation keywords to endpoint metadata."
  ([]
   (println "DEBUG: generate-registry [] ENTER")
   (let [result (generate-registry (load-openapi-spec))]
     (println "DEBUG: generate-registry [] EXIT")
     result))
  ([openapi]
   (println "DEBUG: generate-registry [openapi] ENTER. Spec nil?" (nil? openapi))
   (let [result (if openapi
                  (->> (extract-endpoints openapi)
                       (map (fn [ep]
                              [(keyword (clojure.string/replace (or (:operation-id ep) (str (:method ep) "-" (:path ep))) #"[\\s/{}]" "-"))
                               (with-meta ep {:doc (:description ep)})]))
                       (into {}))
                  {})]
     (println "DEBUG: generate-registry [openapi] EXIT. Registry size:" (count result))
     result)))

(defonce registry (delay (generate-registry)))

(defn get-registry
  "Returns the registry map."
  []
  @registry)

(defn lookup
  "Lookup endpoint metadata by operation keyword."
  [op]
  (get (get-registry) op))