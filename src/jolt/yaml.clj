(ns jolt.yaml
  "YAML parsing for Jolt, bound to the system libyaml through jolt.ffi.

  **(load s)** — parse a single YAML document into Clojure data.
  **(load-all s)** — parse a multi-document YAML stream.
  **(dump x)** — serialize Clojure data to a YAML string (pure-Clojure, no C emitter).

  The loader builds maps, vectors, strings, numbers, booleans, and nil from
  libyaml's event stream. Anchors and aliases are resolved. Keywords are opt-in:
  pass {:keywords true} to stringify map keys as keywords.

  The dumper writes block-scalar YAML: strings quoted when ambiguous, nested
  collections indented two spaces. Numbers, booleans, and nil round-trip
  faithfully."
  (:require [jolt.yaml.ffi :as ffi]
            [clojure.string :as str]))

;; --- type coercion helpers ---------------------------------------------------

(defn- infer-type [s]
  (cond
    (nil? s)              nil
    (= s "true")          true
    (= s "false")         false
    (= s "~")             nil
    (= s "null")          nil
    (re-matches #"^-?\d+\.\d+(?:[eE][+-]?\d+)?$" s) (Double/parseDouble s)
    (re-matches #"^-?\d+$" s) (Long/parseLong s)
    :else                 s))

;; --- parser lifecycle (volatile for safe double-free) ------------------------

(defn- make-parser [s]
  (let [parser (ffi/create-parser)
        bytes  (.getBytes s "UTF-8")
        n      (alength bytes)
        buf    (ffi/alloc (max 1 n))]
    (ffi/write-array buf bytes)
    (ffi/set-input-string parser buf n)
    (volatile! {:parser parser :buf buf})))

(defn- destroy-parser! [vstate]
  (let [state @vstate]
    (when-let [p (:parser state)]
      (ffi/destroy-parser p)
      (when-let [b (:buf state)]
        (ffi/free b))
      (vreset! vstate {:parser nil :buf nil}))))

;; --- event pump: one at a time -----------------------------------------------

(defn- next-event
  "Read the next event from the parser. Returns [t evt-pointer] or nil at
  end-of-stream (parser already destroyed)."
  [vstate]
  (let [parser (:parser @vstate)]
    (if (ffi/null? parser)
      nil
      (let [evt (ffi/alloc-event)]
        (if (ffi/parse-next parser evt)
          (let [t (ffi/event-type evt)]
            (if (= t ffi/YAML-STREAM-END-EVENT)
              (do (ffi/free-event evt) (destroy-parser! vstate) nil)
              [t evt]))
          (do (ffi/free-event evt) (destroy-parser! vstate) nil))))))

;; --- recursive descent reader ------------------------------------------------
;; `ctx` = {:anchors {} :opts {}} — pure data, threaded through recursion.

(defn- read-scalar [ctx evt]
  (let [sv  (ffi/read-scalar evt)
        val (infer-type (:value sv))
        anchor (:anchor sv)]
    (ffi/free-event evt)
    [(if anchor (assoc-in ctx [:anchors anchor] val) ctx) val]))

(defn- read-alias [ctx evt]
  (let [anchor (ffi/alias-anchor evt)
        val    (get (:anchors ctx) anchor)]
    (ffi/free-event evt)
    [ctx val]))

(defn- read-value [vstate ctx t evt]
  (case t
    6  (read-scalar ctx evt)         ;; SCALAR
    5  (read-alias ctx evt)          ;; ALIAS
    7  (let [[c v] (read-sequence vstate ctx nil evt)]  ;; SEQ-START
         [c v])
    9  (let [[c v] (read-mapping vstate ctx nil evt)]   ;; MAP-START
         [c v])
    8  (do (ffi/free-event evt) [ctx nil])  ;; SEQ-END
    10 (do (ffi/free-event evt) [ctx nil])  ;; MAP-END
    ;; skip structural
    (do (ffi/free-event evt) [ctx ::skip])))

(defn- read-sequence [vstate ctx anchor evt]
  (ffi/free-event evt)
  (loop [items []
         c     ctx]
    (if-let [[t e] (next-event vstate)]
      (if (= t 8)                               ;; SEQ-END
        (let [result (vec items)]
          (ffi/free-event e)
          (let [c' (if anchor (assoc-in c [:anchors anchor] result) c)]
            [c' result]))
        (let [[c' item] (read-value vstate c t e)]
          (if (= item ::skip)
            (recur items c')
            (recur (conj items item) c'))))
      [ctx nil])))

(defn- read-mapping [vstate ctx anchor evt]
  (ffi/free-event evt)
  (let [kw (:keywords (:opts ctx))]
    (loop [pairs {}
           c     ctx]
      (if-let [[t e] (next-event vstate)]
        (if (= t 10)                               ;; MAP-END
          (let [result pairs]
            (ffi/free-event e)
            (let [c' (if anchor (assoc-in c [:anchors anchor] result) c)]
              [c' result]))
          (let [[c' k-raw] (read-value vstate c t e)
                ;; convert key to keyword if :keywords is true
                k (if (and kw (instance? String k-raw))
                    (keyword k-raw)
                    k-raw)
                [c''' v] (if-let [[t2 e2] (next-event vstate)]
                           (read-value vstate c' t2 e2)
                           [c' nil])]
            (if (and k v (not= k ::skip) (not= v ::skip))
              (recur (assoc pairs k v) c''')
              (recur pairs c''))))
        [ctx nil]))))

;; --- document pump -----------------------------------------------------------

(defn- read-document-body [vstate ctx]
  (if-let [[t evt] (next-event vstate)]
    (case t
      6  (let [[c v] (read-scalar ctx evt)]
           (if-let [[t2 e2] (next-event vstate)]
             (when (= t2 4) (ffi/free-event e2))
             nil)
           [c v])
      7  (let [[c v] (read-sequence vstate ctx nil evt)]
           (if-let [[t2 e2] (next-event vstate)]
             (when (= t2 4) (ffi/free-event e2))
             nil)
           [c v])
      9  (let [[c v] (read-mapping vstate ctx nil evt)]
           (if-let [[t2 e2] (next-event vstate)]
             (when (= t2 4) (ffi/free-event e2))
             nil)
           [c v])
      5  (read-alias ctx evt)
      4  (do (ffi/free-event evt) [ctx nil])
      (do (ffi/free-event evt) (read-document-body vstate ctx)))
    [ctx nil]))

(defn- read-document [vstate ctx]
  (if-let [[t evt] (next-event vstate)]
    (case t
      1  (do (ffi/free-event evt) (recur vstate ctx))   ;; STREAM-START
      3  (do (ffi/free-event evt) (read-document-body vstate ctx))  ;; DOC-START
      4  (do (ffi/free-event evt) [ctx nil])             ;; DOC-END
      6  (read-scalar ctx evt)   ;; SCALAR (shorthand)
      7  (read-sequence vstate ctx nil evt)               ;; SEQ-START (shorthand)
      9  (read-mapping vstate ctx nil evt)                 ;; MAP-START (shorthand)
      (do (ffi/free-event evt) (recur vstate ctx)))
    [ctx nil]))

;; --- public API --------------------------------------------------------------

(defn load
  "Parse a single YAML document from string s into Clojure data.

  Optional second argument is a map of options:
    :keywords true  — convert unquoted string map keys to keywords

  Returns the first document in the stream."
  [s & [opts]]
  (let [vstate (make-parser s)
        ctx    {:anchors {} :opts (or opts {})}]
    (try
      (let [[_ val] (read-document vstate ctx)]
        val)
      (finally
        (destroy-parser! vstate)))))

(defn load-all
  "Parse all documents in a multi-document YAML stream into a seq."
  [s & [opts]]
  (let [vstate (make-parser s)
        ctx    {:anchors {} :opts (or opts {})}]
    (try
      (loop [docs []
             c    ctx]
        (let [[c' val] (read-document vstate c)]
          (if (nil? val)
            docs
            (recur (conj docs val) c'))))
      (finally
        (destroy-parser! vstate)))))

;; --- pure-Clojure YAML dumper -----------------------------------------------

(defn- needs-quoting? [s]
  (or (re-find #"[\s:#\[\]{},&\*\?!\|>\-'\"%@`]" s)
      (re-find #"^(true|false|null|yes|no|on|off|\d)" s)
      (str/blank? s)
      (= "" s)))

(defn- quote-string [s]
  (if (needs-quoting? s)
    (str "'" (str/replace s "'" "''") "'")
    s))

(defn- dump-seq [xs depth]
  (str/join "\n"
            (for [x xs]
              (str "- " (dump-internal x (inc depth) false)))))

(defn- dump-map [m depth]
  (let [indent (apply str (repeat (* depth 2) \space))]
    (str/join "\n"
              (for [[k v] m]
                (str indent
                     (dump-internal k (inc depth) true) ": "
                     (dump-internal v (inc depth) true))))))

(defn- dump-internal [x depth in-map?]
  (cond
    (nil? x)               "~"
    (instance? Boolean x)  (if x "true" "false")
    (number? x)            (str x)
    (keyword? x)           (str (name x))
    (instance? String x)   (quote-string x)
    (sequential? x)        (str "\n" (dump-seq x depth))
    (map? x)               (str "\n" (dump-map x depth))
    :else                  (quote-string (str x))))

(defn dump
  "Serialize x to a YAML string (pure-Clojure, no C emitter).
  Strings are quoted when ambiguous. Collections are block-style with
  two-space indentation."
  [x]
  (str/trim (dump-internal x 0 false)))

(def generate-string dump)
