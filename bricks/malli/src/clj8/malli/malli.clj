(ns clj8.malli.malli
  "Converts OpenAPI schemas to Malli schemas for validation, coercion, and generation."
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [malli.generator :as mg]
            [clojure.string :as str]))

(defn openapi-type->malli
  "Converts OpenAPI type to Malli schema type.
  
  Args:
    type-def: OpenAPI type definition map
    
  Returns:
    Malli schema"
  [type-def]
  (let [type (get type-def "type")
        format (get type-def "format")
        enum (get type-def "enum")]
    (cond
      enum [:enum (mapv keyword enum)]

      (= type "string") (case format
                          "date-time" :string  ; Could be enhanced with regex
                          "date" :string
                          "uuid" :string
                          :string)

      (= type "integer") (case format
                           "int32" :int
                           "int64" :int
                           :int)

      (= type "number") (case format
                          "float" :double
                          "double" :double
                          :double)

      (= type "boolean") :boolean

      (= type "array") (if-let [items (get type-def "items")]
                         [:vector (openapi-type->malli items)]
                         [:vector :any])

      (= type "object") (if-let [props (get type-def "properties")]
                          [:map (convert-object-properties props type-def)]
                          [:map])

      :else :any)))

(defn convert-object-properties
  "Converts OpenAPI object properties to Malli map schema.
  
  Args:
    properties: OpenAPI properties map
    type-def: Full OpenAPI type definition (for required fields)
    
  Returns:
    Vector of Malli map entries"
  [properties type-def]
  (let [required-fields (set (get type-def "required" []))]
    (mapv (fn [[prop-name prop-def]]
            (let [key (keyword prop-name)
                  schema (openapi-type->malli prop-def)
                  optional? (not (contains? required-fields prop-name))]
              (if optional?
                [key {:optional true} schema]
                [key schema])))
          properties)))

(defn resolve-ref
  "Resolves OpenAPI $ref to actual schema definition.
  
  Args:
    ref-str: $ref string like '#/components/schemas/Pod'
    spec: Full OpenAPI specification
    
  Returns:
    Resolved schema definition"
  [ref-str spec]
  (let [path (-> ref-str
                 (str/replace "#/" "")
                 (str/split #"/"))]
    (get-in spec path)))

(defn convert-schema
  "Converts OpenAPI schema to Malli schema, resolving references.
  
  Args:
    schema-def: OpenAPI schema definition (may contain $ref)
    spec: Full OpenAPI specification for reference resolution
    
  Returns:
    Malli schema"
  [schema-def spec]
  (if-let [ref (get schema-def "$ref")]
    (let [resolved (resolve-ref ref spec)]
      (convert-schema resolved spec))
    (openapi-type->malli schema-def)))

(defn extract-schemas
  "Extracts all schemas from OpenAPI spec and converts to Malli.
  
  Args:
    spec: OpenAPI specification
    
  Returns:
    Map of schema-name -> malli-schema"
  [spec]
  (let [schemas (get-in spec ["components" "schemas"])]
    (->> schemas
         (map (fn [[schema-name schema-def]]
                [(keyword schema-name) (convert-schema schema-def spec)]))
         (into {}))))

(defn validate-with-schema
  "Validates data against a Malli schema.
  
  Args:
    schema: Malli schema
    data: Data to validate
    
  Returns:
    {:valid? boolean :errors [...] :data data}"
  [schema data]
  (let [valid? (m/validate schema data)]
    {:valid? valid?
     :errors (when-not valid? (me/humanize (m/explain schema data)))
     :data data}))

(defn generate-sample
  "Generates sample data from Malli schema.
  
  Args:
    schema: Malli schema
    
  Returns:
    Generated sample data"
  [schema]
  (mg/generate schema))

(defn create-coercer
  "Creates a coercer for transforming data to match schema.
  
  Args:
    schema: Malli schema
    
  Returns:
    Coercion function"
  [schema]
  (m/coercer schema mt/string-transformer))

;; Schema registry for caching converted schemas
(defonce ^:private schema-registry (atom {}))

(defn get-schema
  "Gets cached Malli schema by name, converting if needed.
  
  Args:
    schema-name: Keyword name of schema
    spec: OpenAPI specification (optional, for conversion)
    
  Returns:
    Malli schema or nil"
  [schema-name & [spec]]
  (or (get @schema-registry schema-name)
      (when spec
        (let [schemas (extract-schemas spec)
              schema (get schemas schema-name)]
          (when schema
            (swap! schema-registry assoc schema-name schema)
            schema)))))

(defn register-schema!
  "Registers a Malli schema in the cache.
  
  Args:
    schema-name: Keyword name
    schema: Malli schema
    
  Returns:
    schema"
  [schema-name schema]
  (swap! schema-registry assoc schema-name schema)
  schema)

(defn clear-registry!
  "Clears the schema registry cache."
  []
  (reset! schema-registry {}))
