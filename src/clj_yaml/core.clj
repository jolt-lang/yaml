(ns clj-yaml.core
  "clj-commons/clj-yaml compatibility layer over jolt.yaml.
  Provides the standard clj-yaml API surface: parse-string, generate-string."
  (:require [jolt.yaml :as yaml]))

;; clj-yaml's default option: keywords true (unlike jolt.yaml/load's default)
(def ^:private defaults {:keywords true})

(defn parse-string
  "Parse a YAML string into Clojure data.
  Like clj-yaml: string map keys are converted to keywords by default."
  ([s] (parse-string s {}))
  ([s opts]
   (yaml/load s (merge defaults opts))))

(defn parse-stream
  "Parse a stream that produces YAML strings. Right now reads the whole
  string and delegates to parse-string."
  [rdr]
  (parse-string (slurp rdr)))

(defn generate-string
  "Serialize Clojure data to a YAML string."
  [x]
  (yaml/dump x))
