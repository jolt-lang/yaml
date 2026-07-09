(ns jolt.yaml.snakeyaml
  "SnakeYAML engine v2 surface over the libyaml-backed event pump.

  Registers the `org.snakeyaml.engine.v2` classes (LoadSettings, Parse, and the
  event types) that yamlscript's parser.clj expects. Each SnakeYAML event is a
  jolt.host tagged-table with a `:class` entry for (class e) dispatch, plus the
  methods the parser reads (.getAnchor / .getTag / .getValue / .getStartMark /
  .getEndMark / isFlow / .getScalarStyle / .getAlias).

  `java.util.Optional` is already shimmed in jolt-core
  (host/chez/java/host-static-classes.ss) — .of, .empty, .orElse, .get all work
  on jolt values directly."
  (:require [jolt.yaml.ffi :as ffi]
            [jolt.yaml :as yaml]
            [clojure.string :as str]))

;; --- hierarchy FQNs ----------------------------------------------------------

(def ^:private PKG "org.snakeyaml.engine.v2")
(def ^:private EVT (str PKG ".events"))
(def ^:private API (str PKG ".api"))
(def ^:private LOW (str PKG ".api.lowlevel"))
(def ^:private EXC (str PKG ".exceptions"))

;; Event hierarchy used by parser.clj's instance? checks:
;;   Event
;;     NodeEvent              (getAnchor, getStartMark, getEndMark)
;;       AliasEvent           (getAlias)
;;       ScalarEvent          (getValue, getTag, getScalarStyle)
;;       CollectionStartEvent (getTag, isFlow)
;;         SequenceStartEvent
;;         MappingStartEvent
;;     DocumentStartEvent
;;     DocumentEndEvent
;;     SequenceEndEvent
;;     MappingEndEvent

(defn- tag [n] (keyword "jolt.snakeyaml" n))
(defn- fqn [p n] (str p "." n))

;; --- tagged-table helpers (mirrors jolt.xml's pattern) -----------------------

(defn- tt [tag & {:keys [class]}]
  (let [t (jolt.host/tagged-table tag)]
    (when class
      (jolt.host/ref-put! t :class class))
    t))

(defn- tget [t k] (jolt.host/ref-get t k))
(defn- tput! [t k v] (jolt.host/ref-put! t k v))

;; --- Mark --------------------------------------------------------------------

(defn- make-mark [index line column]
  (doto (tt :jolt.snakeyaml/Mark :class (fqn EXC "Mark"))
    (tput! :index index)
    (tput! :line line)
    (tput! :column column)))

(defn- parse-mark [event offset]
  (let [idx  (ffi/read event :size_t offset)
        line (ffi/read event :size_t (+ offset 8))
        col  (ffi/read event :size_t (+ offset 16))]
    (make-mark idx line col)))

;; --- event construction from libyaml event pointer ---------------------------

(defn- build-scalar [event]
  (let [sv (ffi/read-scalar event)
        obj (doto (tt :jolt.snakeyaml/ScalarEvent
                     :class (fqn EVT "ScalarEvent"))
              (tput! :value (:value sv))
              (tput! :style (case (:style sv)
                               1 ":"     ;; YAML-PLAIN-SCALAR-STYLE (observed)
                               2 "'"     ;; YAML-SINGLE-QUOTED-SCALAR-STYLE
                               3 "\""    ;; YAML-DOUBLE-QUOTED-SCALAR-STYLE
                               4 "|"     ;; YAML-LITERAL-SCALAR-STYLE
                               5 ">"     ;; YAML-FOLDED-SCALAR-STYLE
                               ":"))
              (tput! :anchor (some-> (:anchor sv) java.util.Optional/of))
              (tput! :tag (some-> (:tag sv) java.util.Optional/of))
              (tput! :start-mark (parse-mark event ffi/EVENT-START-MARK))
              (tput! :end-mark (parse-mark event ffi/EVENT-END-MARK)))]
    obj))

(defn- build-alias [event]
  (let [anchor (ffi/alias-anchor event)]
    (doto (tt :jolt.snakeyaml/AliasEvent
             :class (fqn EVT "AliasEvent"))
      (tput! :alias anchor)
      (tput! :anchor (java.util.Optional/of anchor))
      (tput! :start-mark (parse-mark event ffi/EVENT-START-MARK))
      (tput! :end-mark (parse-mark event ffi/EVENT-END-MARK)))))

(defn- build-sequence-start [event]
  (let [sv (ffi/read-collection-start event)]
    (doto (tt :jolt.snakeyaml/SequenceStartEvent
             :class (fqn EVT "SequenceStartEvent"))
      (tput! :flow (= (:style sv) ffi/YAML-FLOW-SEQUENCE-STYLE))
      (tput! :anchor (some-> (:anchor sv) java.util.Optional/of))
      (tput! :tag (some-> (:tag sv) java.util.Optional/of))
      (tput! :start-mark (parse-mark event ffi/EVENT-START-MARK))
      (tput! :end-mark (parse-mark event ffi/EVENT-END-MARK)))))

(defn- build-mapping-start [event]
  (let [sv (ffi/read-collection-start event)]
    (doto (tt :jolt.snakeyaml/MappingStartEvent
             :class (fqn EVT "MappingStartEvent"))
      (tput! :flow (= (:style sv) ffi/YAML-FLOW-MAPPING-STYLE))
      (tput! :anchor (some-> (:anchor sv) java.util.Optional/of))
      (tput! :tag (some-> (:tag sv) java.util.Optional/of))
      (tput! :start-mark (parse-mark event ffi/EVENT-START-MARK))
      (tput! :end-mark (parse-mark event ffi/EVENT-END-MARK)))))

(defn- build-document-start [event]
  (doto (tt :jolt.snakeyaml/DocumentStartEvent
           :class (fqn EVT "DocumentStartEvent"))
    (tput! :start-mark (parse-mark event ffi/EVENT-START-MARK))
    (tput! :end-mark (parse-mark event ffi/EVENT-END-MARK))))

(defn- build-document-end [event]
  (doto (tt :jolt.snakeyaml/DocumentEndEvent
           :class (fqn EVT "DocumentEndEvent"))
    (tput! :start-mark (parse-mark event ffi/EVENT-START-MARK))
    (tput! :end-mark (parse-mark event ffi/EVENT-END-MARK))))

(defn- build-sequence-end [event]
  (doto (tt :jolt.snakeyaml/SequenceEndEvent
           :class (fqn EVT "SequenceEndEvent"))
    (tput! :start-mark (parse-mark event ffi/EVENT-START-MARK))
    (tput! :end-mark (parse-mark event ffi/EVENT-END-MARK))))

(defn- build-mapping-end [event]
  (doto (tt :jolt.snakeyaml/MappingEndEvent
           :class (fqn EVT "MappingEndEvent"))
    (tput! :start-mark (parse-mark event ffi/EVENT-START-MARK))
    (tput! :end-mark (parse-mark event ffi/EVENT-END-MARK))))

;; --- event pump: libyaml -> SnakeYAML event seq ------------------------------

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

(defn- read-event [vstate]
  "Read the next non-stream event. Returns the event obj or nil at EOF.
   Destroys parser+buf on STREAM-END or parse failure."
  (let [parser (:parser @vstate)]
    (when-not (ffi/null? parser)
      (let [evt (ffi/alloc-event)]
        (if (ffi/parse-next parser evt)
          (let [t (ffi/event-type evt)]
            (if (= t ffi/YAML-STREAM-END-EVENT)
              ;; STREAM-END — clean up and return nil
              (do (ffi/free-event evt)
                  (destroy-parser! vstate)
                  nil)
              (if (= t ffi/YAML-STREAM-START-EVENT)
                ;; STREAM-START — skip
                (do (ffi/free-event evt)
                    (recur vstate))
                ;; Real event — build the tagged-table obj
                (let [obj (case t
                            3 (build-document-start evt)
                            4 (build-document-end evt)
                            5 (build-alias evt)
                            6 (build-scalar evt)
                            7 (build-sequence-start evt)
                            8 (build-sequence-end evt)
                            9 (build-mapping-start evt)
                            10 (build-mapping-end evt)
                            nil)]
                  (ffi/free-event evt)
                  obj))))
          (do (ffi/free-event evt)
              (destroy-parser! vstate)
              nil))))))

(defn parse-string
  "Parse YAML string into a lazy seq of SnakeYAML event objects."
  [^String s]
  (let [vstate (make-parser s)]
    (letfn [(pump []
              (lazy-seq
                (when-let [obj (read-event vstate)]
                  (cons obj (pump)))))]
      (pump))))

;; --- class hierarchy registration --------------------------------------------

(defn- register-event-methods! []
  ;; Register each method under BOTH keyword tag (runtime dispatch via tag)
  ;; and FQN string (dispatch via imported-class type hint like ^NodeEvent).
  (let [reg!    (fn [id ms] (__register-class-methods! id ms))
        mark-fn   (fn [self] (if-let [m (tget self :start-mark)]
                               (java.util.Optional/of m)
                               java.util.Optional/empty))
        end-fn    (fn [self] (if-let [m (tget self :end-mark)]
                               (java.util.Optional/of m)
                               java.util.Optional/empty))
        anchor-fn (fn [self]
                     (let [a (tget self :anchor)]
                       (if a a (java.util.Optional/empty))))
        tag-fn    (fn [self]
                     (let [t (tget self :tag)]
                       (if t t (java.util.Optional/empty))))
        flow-fn   (fn [self] (tget self :flow))]

    ;; Mark (keyword for tag dispatch, FQN for ^Mark type hint)
    (reg! :jolt.snakeyaml/Mark
      {"getLine"   (fn [self] (tget self :line))
       "getColumn" (fn [self] (tget self :column))
       "getIndex"  (fn [self] (tget self :index))})
    (reg! (fqn EXC "Mark")
      {"getLine"   (fn [self] (tget self :line))
       "getColumn" (fn [self] (tget self :column))
       "getIndex"  (fn [self] (tget self :index))})

    ;; ── Event — ^Event type hint ──
    (reg! (fqn EVT "Event") {"getStartMark" mark-fn "getEndMark" end-fn})

    ;; ── Every concrete event: getStartMark/getEndMark + getAnchor + getTag + isFlow ──
    ;;    (instance? with Vars is broken (#39), so ALL events reach the tag/anchor
    ;;     code paths in yamlscript.parser/event-start.)
    (let [base-ms {"getStartMark" mark-fn "getEndMark" end-fn
                   "getAnchor" anchor-fn "getTag" tag-fn
                   "isFlow" flow-fn}]
      (doseq [[kw nm] [[:jolt.snakeyaml/ScalarEvent "ScalarEvent"]
                        [:jolt.snakeyaml/AliasEvent "AliasEvent"]
                        [:jolt.snakeyaml/SequenceStartEvent "SequenceStartEvent"]
                        [:jolt.snakeyaml/MappingStartEvent "MappingStartEvent"]
                        [:jolt.snakeyaml/SequenceEndEvent "SequenceEndEvent"]
                        [:jolt.snakeyaml/MappingEndEvent "MappingEndEvent"]
                        [:jolt.snakeyaml/DocumentStartEvent "DocumentStartEvent"]
                        [:jolt.snakeyaml/DocumentEndEvent "DocumentEndEvent"]]]
        (reg! kw  base-ms)
        (reg! (fqn EVT nm) base-ms)))

    ;; ── ScalarEvent ──
    (let [ms {"getValue"       (fn [self] (tget self :value))
              "getTag"         tag-fn
              "getScalarStyle" (fn [self]
                                 (let [s (tget self :style)]
                                   (reify Object (toString [_] s))))}]
      (reg! :jolt.snakeyaml/ScalarEvent ms)
      (reg! (fqn EVT "ScalarEvent") ms))

    ;; ── AliasEvent ──
    (let [ms {"getAlias" (fn [self] (tget self :alias))}]
      (reg! :jolt.snakeyaml/AliasEvent ms)
      (reg! (fqn EVT "AliasEvent") ms))

    ;; ── CollectionStartEvent — ^CollectionStartEvent type hint ──
    (let [ms {"getTag" tag-fn "isFlow" flow-fn}]
      (reg! (fqn EVT "CollectionStartEvent") ms)
      (doseq [[kw nm] [[:jolt.snakeyaml/SequenceStartEvent "SequenceStartEvent"]
                        [:jolt.snakeyaml/MappingStartEvent "MappingStartEvent"]]]
        (reg! kw  ms)
        (reg! (fqn EVT nm) ms))))
  nil)

(defn- register-hierarchy! []
  (jolt.host/register-class-supers! (fqn EVT "Event") [])
  (jolt.host/register-class-supers! (fqn EVT "NodeEvent") [(fqn EVT "Event")])
  (jolt.host/register-class-supers! (fqn EVT "AliasEvent") [(fqn EVT "NodeEvent")])
  (jolt.host/register-class-supers! (fqn EVT "ScalarEvent") [(fqn EVT "NodeEvent")])
  (jolt.host/register-class-supers! (fqn EVT "CollectionStartEvent") [(fqn EVT "NodeEvent")])
  (jolt.host/register-class-supers! (fqn EVT "SequenceStartEvent") [(fqn EVT "CollectionStartEvent")])
  (jolt.host/register-class-supers! (fqn EVT "MappingStartEvent") [(fqn EVT "CollectionStartEvent")])
  (jolt.host/register-class-supers! (fqn EVT "SequenceEndEvent") [(fqn EVT "Event")])
  (jolt.host/register-class-supers! (fqn EVT "MappingEndEvent") [(fqn EVT "Event")])
  (jolt.host/register-class-supers! (fqn EVT "DocumentStartEvent") [(fqn EVT "Event")])
  (jolt.host/register-class-supers! (fqn EVT "DocumentEndEvent") [(fqn EVT "Event")])
  (jolt.host/register-class-supers! (fqn EXC "Mark") [])
  nil)

(defn- register-load-settings! []
  ;; LoadSettings: .builder -> LoadSettingsBuilder, .build -> LoadSettings (inert)
  ;; Register under BOTH FQN and short name so (LoadSettings/builder) resolves.
  (let [fqn (fqn API "LoadSettings")
        short "LoadSettings"]
    (doseq [n [fqn short]]
      (__register-class-statics! n
        {"builder" (fn [] (tt :jolt.snakeyaml/LoadSettingsBuilder))})))
  (__register-class-methods! :jolt.snakeyaml/LoadSettingsBuilder
    {"build" (fn [self] (tt :jolt.snakeyaml/LoadSettings))})
  nil)

(defn- register-parse! []
  ;; Parse ctor takes a LoadSettings, .parseString returns event iterable
  ;; Register under BOTH FQN and short name so (new Parse ...) or (Parse. ...) resolves.
  (let [fqn (fqn LOW "Parse")
        short "Parse"]
    (doseq [n [fqn short]]
      (__register-class-ctor! n
        (fn [& args] (tt :jolt.snakeyaml/Parse)))))
  ;; .parseString returns a seq of events via our event pump
  (__register-class-methods! :jolt.snakeyaml/Parse
    {"parseString" (fn [self ^String s] (parse-string s))})
  nil)

(defn- intern-class-var!
  "Create a var in the FQN namespace so chez-runtime-import finds it.
  The var holds the FQN string, matching what (class event) returns from the htable :class field."
  [fqn]
  (let [ns-sym (symbol (subs fqn 0 (str/last-index-of fqn ".")))
        name-sym (symbol (subs fqn (inc (str/last-index-of fqn "."))))
        _ (create-ns ns-sym)]
    (intern ns-sym name-sym fqn)))

(defn- register-event-classes! []
  (doseq [evt ["Event" "NodeEvent" "AliasEvent" "ScalarEvent"
               "CollectionStartEvent" "SequenceStartEvent" "MappingStartEvent"
               "SequenceEndEvent" "MappingEndEvent"
               "DocumentStartEvent" "DocumentEndEvent"]]
    (intern-class-var! (fqn EVT evt)))
  (intern-class-var! (fqn EXC "Mark"))
  nil)

(defn install!
  "Register all SnakeYAML v2 classes and methods. Called at top-level."
  []
  (register-hierarchy!)
  (register-event-methods!)
  (register-load-settings!)
  (register-parse!)
  (register-event-classes!)
  nil)

(install!)
