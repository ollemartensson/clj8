(ns clj8.malli.malli
  (:require [malli.core :as m]
            [malli.generator :as mg] ; Added malli.generator
            [clojure.string :as str]
            [clj8.registry.registry :as reg])) ; Assuming registry loads the spec

;; Malli schema registry
(defonce malli-schema-registry (atom {}))

(defn openapi-type->malli-type [openapi-type format]
  (case openapi-type
    "string" (case format
               "date" :string ; refine later with malli.experimental.time if needed
               "date-time" :string ; refine later
               "byte" :string ; base64 encoded
               "binary" :string ; sequence of bytes
               :string)
    "number" (case format
               "float" :float
               "double" :double
               :double) ; Default for number
    "integer" (case format
                "int32" :int
                "int64" :int
                :int) ; Default for integer
    "boolean" :boolean
    "array" :vector
    "object" :map
    nil)) ; For refs or other complex types handled separately

(defn resolve-ref [ref-str openapi-spec]
  (let [parts (str/split ref-str #"/")
        path (rest parts)] ; drop #
    (get-in openapi-spec path)))

(declare openapi-schema->malli-schema) ; Declare for mutual recursion

(defn properties->malli-schema [properties required openapi-spec definitions-path]
  (into [:map]
        (for [[prop-name prop-schema] properties]
          (let [resolved-prop-schema (if (:type prop-schema)
                                       prop-schema
                                       (resolve-ref (:$ref prop-schema) openapi-spec))]
            [(keyword prop-name)
             (if (some #(= prop-name %) required)
               (openapi-schema->malli-schema resolved-prop-schema openapi-spec definitions-path)
               [:orn
                [:required (openapi-schema->malli-schema resolved-prop-schema openapi-spec definitions-path)]
                [:optional [:maybe (openapi-schema->malli-schema resolved-prop-schema openapi-spec definitions-path)]]])]))))

(defn openapi-schema->malli-schema
  "Converts a single OpenAPI schema definition to a Malli schema."
  [openapi-schema openapi-spec definitions-path]
  (when openapi-schema
    (cond
      (:$ref openapi-schema)
      (let [ref-path (get openapi-schema :$ref)
            ref-name (last (str/split ref-path #"/"))
            cached (get @malli-schema-registry (keyword ref-name))]
        (if cached
          cached
          (let [resolved-schema (resolve-ref ref-path openapi-spec)]
            (if (= (conj definitions-path ref-name) definitions-path) ; Avoid infinite loop for self-references
              (keyword ref-name) ; Return keyword for recursive structures
              (openapi-schema->malli-schema resolved-schema openapi-spec (conj definitions-path ref-name))))))

      :else
      (let [openapi-type (get openapi-schema "type")
            format (get openapi-schema "format")]
        (case openapi-type
          "object"
          (if-let [props (get openapi-schema "properties")]
            (properties->malli-schema props (get openapi-schema "required") openapi-spec definitions-path)
            (if-let [add-props (get openapi-schema "additionalProperties")]
              (if (true? add-props)
                [:map-of :keyword :any] ; Allows any additional properties
                [:map-of :keyword (openapi-schema->malli-schema add-props openapi-spec definitions-path)])
              :map)) ; Default for object if no properties or additionalProperties

          "array"
          (if-let [items-schema (get openapi-schema "items")]
            [:vector (openapi-schema->malli-schema items-schema openapi-spec definitions-path)]
            [:vector :any]) ; Default for array if no items schema

          (openapi-type->malli-type openapi-type format))))))


(defn build-malli-schemas-from-openapi
  "Builds Malli schemas from the #/components/schemas part of an OpenAPI spec."
  ([]
   (let [openapi-spec (reg/load-openapi-spec)]
     (build-malli-schemas-from-openapi openapi-spec)))
  ([openapi-spec]
   (let [component-schemas (get-in openapi-spec ["components" "schemas"])]
     (doseq [[schema-name schema-def] component-schemas]
       (when-not (get @malli-schema-registry (keyword schema-name))
         ;; Register with a placeholder for circular dependencies
         (swap! malli-schema-registry assoc (keyword schema-name) [:schema {:registry @malli-schema-registry} (keyword schema-name)])
         (let [malli-s (openapi-schema->malli-schema schema-def openapi-spec [(keyword schema-name)])]
           (swap! malli-schema-registry assoc (keyword schema-name) malli-s))))
     @malli-schema-registry)))

;; Initialize the registry
(defonce malli-registry (delay (build-malli-schemas-from-openapi)))

(defn get-malli-schema
  "Retrieves a Malli schema by its keyword name from the registry."
  [schema-key]
  (get @malli-registry schema-key))

(defn validate
  "Validates data against a Malli schema."
  [schema-key data]
  (m/validate (get-malli-schema schema-key) data))

(defn explain
  "Explains why data fails validation against a Malli schema."
  [schema-key data]
  (m/explain (get-malli-schema schema-key) data))

(defn coerce
  "Coerces data using a Malli schema."
  [schema-key data]
  (m/coerce (get-malli-schema schema-key) data))

(defn generate
  "Generates sample data from a Malli schema."
  [schema-key & [opts]] ; Made opts optional and updated function signature
  (mg/generate (get-malli-schema schema-key) opts)) ; Changed to mg/generate

;; Example usage (for dev and testing)
(comment
  (reg/load-openapi-spec) ;; to ensure registry is loaded if not already
  (def k8s-malli-registry (build-malli-schemas-from-openapi))
  (count k8s-malli-registry)

  (get-malli-schema :io.k8s.api.core.v1.Pod)
  (m/schema? (get-malli-schema :io.k8s.api.core.v1.Pod))

  (explain :io.k8s.api.core.v1.Pod {:spec {:containers [{:name "nginx"}]}})

  (validate :io.k8s.api.core.v1.Pod
            {:apiVersion "v1"
             :kind "Pod"
             :metadata {:name "test-pod"}
             :spec {:containers [{:name "my-container"
                                  :image "nginx"}]}})

  (generate :io.k8s.api.core.v1.Pod {:size 2}))