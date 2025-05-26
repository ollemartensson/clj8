(ns dev-test
  "Development namespace for testing in the REPL"
  (:require [clojure.test :as test]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            ;; Require all the test namespaces
            [clj8.registry.registry-test]
            [clj8.client.client-test]
            [clj8.core.core-test]
            [clj8.malli.malli-test]
            [clj8.macros.macros-test]
            ;; Add more test namespaces as they are created
            ))

(defn run-all-tests
  "Run all tests in the project"
  []
  (println "Running all tests...")
  (test/run-all-tests #"clj8\..*-test"))

(defn run-brick-tests
  "Run tests for a specific brick.
   Example: (run-brick-tests :registry)"
  [brick-name]
  (let [brick-ns (str "clj8." (name brick-name) "." (name brick-name) "-test")]
    (println "Running tests for" brick-ns)
    (try
      (test/run-tests (symbol brick-ns))
      (catch Exception e
        (println "Error running tests for" brick-ns ":" (.getMessage e))
        (println "Make sure the namespace exists and is loaded.")))))

(defn list-test-namespaces
  "List all available test namespaces"
  []
  (println "Available test namespaces:")
  (->> (all-ns)
       (filter #(re-matches #"clj8\..*-test" (str %)))
       (sort)
       (map #(println " -" %))
       (doall)))

(defn find-test-files
  "Find all test files in the project"
  []
  (println "Test files in the project:")
  (->> (file-seq (io/file "/Users/olle/src/clj8"))
       (filter #(and (.isFile %)
                     (.endsWith (.getName %) "_test.clj")
                     (str/includes? (.getPath %) "test")))
       (map #(.getPath %))
       (sort)
       (map #(println " -" %))
       (doall)))

(comment
  ;; Example test commands
  (run-all-tests)
  (run-brick-tests :registry)
  (list-test-namespaces)
  (find-test-files))

(println "🧪 Test utilities loaded. Try (dev-test/run-all-tests) or (dev-test/run-brick-tests :registry)")
