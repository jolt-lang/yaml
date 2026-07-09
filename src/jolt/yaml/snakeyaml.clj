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
            [jolt.yaml :as yaml]))

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
                              1 "|"     ;; YAML-PLAIN-SCALAR-STYLE
                              2 "'"     ;; YAML-SINGLE-QUOTED-SCALAR-STYLE
                              3 "\""    ;; YAML-DOUBLE-QUOTED-SCALAR-STYLE
                              4 "|"     ;; YAML-LITERAL-SCALAR-STYLE
                              5 ">"     ;; YAML-FOLDED-SCALAR-STYLE
                              "|"))
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

(defn parse-string
  "Parse YAML string into a lazy seq of SnakeYAML event objects."
  [^String s]
  (let [parser (ffi/create-parser)
        bytes (.getBytes s "UTF-8")
        n     (alength bytes)
        buf   (ffi/alloc (max 1 n))]
    (ffi/write-array buf bytes)
    (ffi/set-input-string parser buf n)
    (letfn [(pump []
              (let [evt (ffi/alloc-event)]
                (if (ffi/parse-next parser evt)
                  (let [t (ffi/event-type evt)]
                    ;; Skip STREAM-START (1) and STREAM-END (2) — libyaml emits
                    ;; these but SnakeYAML's Parse.parseString does not, and
                    ;; STREAM-END signals that the next parse returns false,
                    ;; where we clean up parser+buf.
                    (if (or (= t 1) (= t 2))
                      (do (ffi/free-event evt) (pump))
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
                        (when obj
                          (lazy-seq (cons obj (pump)))))))
                  ;; End of stream — clean up
                  (do (ffi/free-event evt)
                      (ffi/destroy-parser parser)
                      (ffi/free buf)
                      nil))))]
      (pump))))

;; --- class hierarchy registration --------------------------------------------

(defn- register-event-methods! []
  ;; Mark
  (__register-class-methods! :jolt.snakeyaml/Mark
    {"getLine"   (fn [self] (tget self :line))
     "getColumn" (fn [self] (tget self :column))
     "getIndex"  (fn [self] (tget self :index))})

  ;; Event base — getStartMark / getEndMark on every event type, wrapped in Optional
  (doseq [event-tag [:jolt.snakeyaml/ScalarEvent
                     :jolt.snakeyaml/AliasEvent
                     :jolt.snakeyaml/SequenceStartEvent
                     :jolt.snakeyaml/MappingStartEvent
                     :jolt.snakeyaml/SequenceEndEvent
                     :jolt.snakeyaml/MappingEndEvent
                     :jolt.snakeyaml/DocumentStartEvent
                     :jolt.snakeyaml/DocumentEndEvent]]
    (__register-class-methods! event-tag
      {"getStartMark" (fn [self] (if-let [m (tget self :start-mark)]
                                   (java.util.Optional/of m)
                                   java.util.Optional/empty))
       "getEndMark"   (fn [self] (if-let [m (tget self :end-mark)]
                                   (java.util.Optional/of m)
                                   java.util.Optional/empty))}))

  ;; NodeEvent — getAnchor
  (doseq [node-tag [:jolt.snakeyaml/ScalarEvent
                    :jolt.snakeyaml/AliasEvent
                    :jolt.snakeyaml/SequenceStartEvent
                    :jolt.snakeyaml/MappingStartEvent]]
    (__register-class-methods! node-tag
      {"getAnchor" (fn [self] (or (tget self :anchor) java.util.Optional/empty))}))

  ;; ScalarEvent specifics
  (__register-class-methods! :jolt.snakeyaml/ScalarEvent
    {"getValue"       (fn [self] (tget self :value))
     "getTag"         (fn [self] (or (tget self :tag) java.util.Optional/empty))
     "getScalarStyle" (fn [self]
                        (let [s (tget self :style)]
                          ;; Return a simple object with toString matching SnakeYAML's ScalarStyle
                          (reify Object
                            (toString [_] s))))})

  ;; AliasEvent specifics
  (__register-class-methods! :jolt.snakeyaml/AliasEvent
    {"getAlias" (fn [self] (tget self :alias))})

  ;; CollectionStartEvent — getTag, isFlow
  (doseq [coll-tag [:jolt.snakeyaml/SequenceStartEvent
                    :jolt.snakeyaml/MappingStartEvent]]
    (__register-class-methods! coll-tag
      {"getTag" (fn [self] (or (tget self :tag) java.util.Optional/empty))
       "isFlow" (fn [self] (tget self :flow))}))
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
  (__register-class-statics! (fqn API "LoadSettings")
    {"builder" (fn [] (tt :jolt.snakeyaml/LoadSettingsBuilder))})
  (__register-class-methods! :jolt.snakeyaml/LoadSettingsBuilder
    {"build" (fn [self] (tt :jolt.snakeyaml/LoadSettings))})
  nil)

(defn- register-parse! []
  ;; Parse ctor takes a LoadSettings, .parseString returns event iterable
  (__register-class-ctor! (fqn LOW "Parse")
    (fn [& args] (tt :jolt.snakeyaml/Parse)))
  ;; .parseString returns a seq of events via our event pump
  (__register-class-methods! :jolt.snakeyaml/Parse
    {"parseString" (fn [self ^String s] (parse-string s))})
  nil)

(defn install!
  "Register all SnakeYAML v2 classes and methods. Called at top-level."
  []
  (register-hierarchy!)
  (register-event-methods!)
  (register-load-settings!)
  (register-parse!)
  nil)

(install!)
