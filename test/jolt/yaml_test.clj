(ns jolt.yaml-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [jolt.yaml.ffi :as ffi]
            [jolt.yaml :as yaml]
            [jolt.yaml.snakeyaml]
            [clj-yaml.core :as cy]))

;; === jolt.yaml public API ===================================================

(deftest load-scalar
  (is (= "hello" (yaml/load "hello\n"))))

(deftest load-number
  (is (= 42 (yaml/load "42\n"))))

(deftest load-float
  (is (= 3.14 (yaml/load "3.14\n"))))

(deftest load-boolean
  (is (= true (yaml/load "true\n")))
  (is (= false (yaml/load "false\n"))))

(deftest load-nil
  (is (= nil (yaml/load "~\n")))
  (is (= nil (yaml/load "null\n"))))

(deftest load-sequence
  (let [result (yaml/load "[1, 2, 3]\n")]
    (is (= [1 2 3] result))))

(deftest load-block-sequence
  (let [result (yaml/load "- 1\n- 2\n- 3\n")]
    (is (= [1 2 3] result))))

(deftest load-nested-sequence
  (let [result (yaml/load "- [1, 2]\n- [3, 4]\n")]
    (is (= [[1 2] [3 4]] result))))

(deftest load-mapping
  (let [result (yaml/load "a: 1\nb: 2\n")]
    (is (= {"a" 1 "b" 2} result))))

(deftest load-nested-mapping
  (let [result (yaml/load "a:\n  b: 1\n")]
    (is (= {"a" {"b" 1}} result))))

(deftest load-mapping-in-sequence
  (let [result (yaml/load "- a: 1\n  b: 2\n- c: 3\n")]
    (is (= [{"a" 1 "b" 2} {"c" 3}] result))))

(deftest load-keywords
  (let [result (yaml/load "foo: bar\n" {:keywords true})]
    (is (= {:foo "bar"} result))))

(deftest load-keywords-multi-entry
  (let [result (yaml/load "a: 1\nb: 2\n" {:keywords true})]
    (is (= {:a 1 :b 2} result))))

(deftest load-all-multi-doc
  (let [result (yaml/load-all "---\na: 1\n---\nb: 2\n")]
    (is (= [{"a" 1} {"b" 2}] result))))

;; === falsey values, empty and quoted scalars (as clj-yaml reads them) =====

(deftest load-false-and-null-values
  (is (= {"a" false "q" nil "n" nil "r" nil}
         (yaml/load "a: false\nq: ~\nn: null\nr:\n")))
  (is (= [nil false nil] (yaml/load "- ~\n- false\n-\n")))
  (is (= {:a false :b nil} (cy/parse-string "a: false\nb:\n"))))

(deftest load-quoted-scalars-stay-strings
  (is (= {"s" "" "t" "true" "u" "42" "v" "~" "w" "null"}
         (yaml/load "s: ''\nt: 'true'\nu: \"42\"\nv: '~'\nw: 'null'\n"))))

(deftest load-core-schema-case-variants
  (is (= [true false nil nil] (yaml/load "[True, FALSE, Null, NULL]\n"))))

(deftest load-all-null-document
  (is (= [nil 1] (yaml/load-all "--- ~\n--- 1\n"))))

;; === dump layout ============================================================

(deftest dump-block-layout
  (is (= "a: 1\nb:\n  c:\n  - 1\n  - 2\n  d: null\n"
         (yaml/dump (array-map :a 1 :b (array-map :c [1 2] :d nil)))))
  (is (= "- a: 1\n  b: 2\n- - 1\n  - 2\n- x\n"
         (yaml/dump [(array-map :a 1 :b 2) [1 2] "x"])))
  (is (= "a: {}\nb: []\n" (yaml/dump (array-map :a {} :b []))))
  (is (= "hello\n" (yaml/dump "hello")))
  (is (= "a: 1\n" (cy/generate-string {:a 1}))))

(deftest dump-no-trailing-spaces
  (let [s (yaml/dump {"x" {"y" [{"z" 1}]}})]
    (is (not (re-find #" \n" s)))
    (is (= {"x" {"y" [{"z" 1}]}} (yaml/load s)))))

(deftest dump-roundtrips-awkward-strings
  (doseq [v ["true" "True" "~" "null" "NULL" "42" "a\nb" "it's" "x: y" "" "  pad" "tab\there" "back\\slash \"q\""]]
    (is (= {"k" v} (yaml/load (yaml/dump {"k" v}))) (pr-str v))))

(deftest dump-roundtrips-falsey
  (let [m {"a" false "b" nil "c" [nil false]}]
    (is (= m (yaml/load (yaml/dump m))))))

;; === round-trip (dump then load) ============================================

(deftest roundtrip-scalar
  (is (= "hello" (yaml/load (yaml/dump "hello")))))

(deftest roundtrip-number
  (is (= 42 (yaml/load (yaml/dump 42)))))

(deftest roundtrip-float
  (is (= 3.14 (yaml/load (yaml/dump 3.14)))))

(deftest roundtrip-boolean
  (is (= true (yaml/load (yaml/dump true))))
  (is (= false (yaml/load (yaml/dump false)))))

(deftest roundtrip-nil
  (is (= nil (yaml/load (yaml/dump nil)))))

(deftest roundtrip-sequence
  (let [v [1 2 3]]
    (is (= v (yaml/load (yaml/dump v))))))

(deftest roundtrip-map
  (let [m {"a" 1 "b" 2}]
    (is (= m (yaml/load (yaml/dump m))))))

(deftest roundtrip-nested
  (let [data {"name" "example" "values" [1 2 3] "nested" {"x" 10}}]
    (is (= data (yaml/load (yaml/dump data))))))

(deftest roundtrip-keyword-map
  (let [m {:a 1 :b 2}]
    (is (= m (yaml/load (yaml/dump m) {:keywords true})))))

;; === clj-yaml compat ========================================================

(deftest clj-yaml-parse-string
  (is (= {:a 1 :b 2} (cy/parse-string "a: 1\nb: 2"))))

(deftest clj-yaml-generate-string
  (let [s (cy/generate-string {:a 1})]
    (is (string? s))))

;; === SnakeYAML surface ======================================================

(deftest snakeyaml-load-settings
  (let [builder (org.snakeyaml.engine.v2.api.LoadSettings/builder)]
    (is (some? builder))
    (let [settings (.build builder)]
      (is (some? settings)))))

(deftest snakeyaml-parse-scalar
  (let [parser (new org.snakeyaml.engine.v2.api.lowlevel.Parse
                    (.build (org.snakeyaml.engine.v2.api.LoadSettings/builder)))
        events (.parseString parser "hello\n")
        events (remove nil? events)
        ;; skip to first ScalarEvent
        scalar (first (filter #(instance? org.snakeyaml.engine.v2.events.ScalarEvent %) events))]
    (is (some? scalar))
    (is (= "hello" (.getValue scalar)))))

(deftest snakeyaml-event-class
  (let [events (remove nil? (.parseString
                             (new org.snakeyaml.engine.v2.api.lowlevel.Parse
                                  (.build (org.snakeyaml.engine.v2.api.LoadSettings/builder)))
                             "hello\n"))
        scalar (first (filter #(instance? org.snakeyaml.engine.v2.events.ScalarEvent %) events))]
    (is (some? scalar))
    (is (= "class org.snakeyaml.engine.v2.events.ScalarEvent"
           (str (class scalar))))))

(deftest snakeyaml-event-marks
  (let [events (remove nil? (.parseString
                             (new org.snakeyaml.engine.v2.api.lowlevel.Parse
                                  (.build (org.snakeyaml.engine.v2.api.LoadSettings/builder)))
                             "hello\n"))
        scalar (first (filter #(instance? org.snakeyaml.engine.v2.events.ScalarEvent %) events))]
    (let [start-mark (.getStartMark scalar)
          end-mark (.getEndMark scalar)]
      (is (some? (.orElse start-mark nil)))
      (is (some? (.orElse end-mark nil))))))

(deftest snakeyaml-sequence
  (let [events (remove nil? (.parseString
                             (new org.snakeyaml.engine.v2.api.lowlevel.Parse
                                  (.build (org.snakeyaml.engine.v2.api.LoadSettings/builder)))
                             "[1, 2, 3]\n"))
        scalars (filter #(instance? org.snakeyaml.engine.v2.events.ScalarEvent %) events)]
    (is (= 3 (count scalars)))
    (is (= "1" (.getValue (first scalars))))))

(deftest snakeyaml-mapping
  (let [events (remove nil? (.parseString
                             (new org.snakeyaml.engine.v2.api.lowlevel.Parse
                                  (.build (org.snakeyaml.engine.v2.api.LoadSettings/builder)))
                             "a: 1\nb: 2\n"))
        starts (filter #(instance? org.snakeyaml.engine.v2.events.MappingStartEvent %) events)]
    (is (= 1 (count starts)))))

;; === -main ==================================================================

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'jolt.yaml-test)]
    (when (pos? (+ fail error)) (System/exit 1))))
