(ns clj8.test-runner
  "Test runner for Polylith development project."
  (:require [clojure.test :as test]
            [dev-test :as dt]))

(defn run-all-tests
  "Run all clj8 tests for Polylith."
  []
  (println "🧪 Running clj8 comprehensive test suite...")
  (dt/run-all-tests))

(defn -main [& args]
  "Main entry point for Polylith test runner."
  (run-all-tests)
  (System/exit 0))