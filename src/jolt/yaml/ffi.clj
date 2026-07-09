(ns jolt.yaml.ffi
  "Low-level libyaml event-driven parser bindings.

  Struct offsets baked from dev/offsetof.c (ARM64 macOS libyaml 0.2.5):
  Run `cc -o offsetof dev/offsetof.c -lyaml && ./offsetof` to regenerate.

    yaml_event_t (104 bytes):
      type       0   (int enum)
      data       8   (union, 48 bytes)
      start_mark 56  (yaml_mark_t)
      end_mark   80  (yaml_mark_t)

    data.scalar (within the union):
      anchor            0  (yaml_char_t *)
      tag               8  (yaml_char_t *)
      value            16  (yaml_char_t *)
      length           24  (size_t)
      plain_implicit   32  (int)
      quoted_implicit  36  (int)
      style            40  (yaml_scalar_style_t)

    data.alias:      anchor 0
    data.sequence_start: anchor 0 tag 8 implicit 16 style 20
    data.mapping_start:  anchor 0 tag 8 implicit 16 style 20
    data.stream_start:   encoding 0

    yaml_mark_t (24 bytes): index 0 line 8 column 16

  yaml_parser_t: 480 bytes (allocated on the heap, freed with yaml_parser_delete)."
  (:require [jolt.ffi :as ffi]))

;; Re-export jolt.ffi helpers so consumers only need one :require.
(def alloc ffi/alloc)
(def free ffi/free)
(def read ffi/read)
(def write ffi/write)
(def sizeof ffi/sizeof)
(def ptr->string ffi/ptr->string)
(def string->ptr ffi/string->ptr)
(def null ffi/null)
(def null? ffi/null?)
(def load-library ffi/load-library)
(def read-bytes ffi/read-bytes)
(def write-bytes ffi/write-bytes)
(def read-array ffi/read-array)
(def write-array ffi/write-array)

;; --- libyaml function bindings -----------------------------------------------

(ffi/defcfn yaml-parser-initialize      "yaml_parser_initialize"       [:pointer] :int)
(ffi/defcfn yaml-parser-delete          "yaml_parser_delete"           [:pointer] :void)
(ffi/defcfn yaml-parser-set-input-string "yaml_parser_set_input_string" [:pointer :pointer :size_t] :void)
(ffi/defcfn yaml-parser-parse           "yaml_parser_parse"            [:pointer :pointer] :int)
(ffi/defcfn yaml-event-delete           "yaml_event_delete"            [:pointer] :void)

;; --- event type enum ---------------------------------------------------------

(def YAML-NO-EVENT              0)
(def YAML-STREAM-START-EVENT    1)
(def YAML-STREAM-END-EVENT      2)
(def YAML-DOCUMENT-START-EVENT  3)
(def YAML-DOCUMENT-END-EVENT    4)
(def YAML-ALIAS-EVENT           5)
(def YAML-SCALAR-EVENT          6)
(def YAML-SEQUENCE-START-EVENT  7)
(def YAML-SEQUENCE-END-EVENT    8)
(def YAML-MAPPING-START-EVENT   9)
(def YAML-MAPPING-END-EVENT     10)

;; --- scalar style enum -------------------------------------------------------

(def YAML-PLAIN-SCALAR-STYLE        1)
(def YAML-SINGLE-QUOTED-SCALAR-STYLE 2)
(def YAML-DOUBLE-QUOTED-SCALAR-STYLE 3)
(def YAML-LITERAL-SCALAR-STYLE      4)
(def YAML-FOLDED-SCALAR-STYLE       5)

;; --- sequence / mapping style enum -------------------------------------------

(def YAML-BLOCK-SEQUENCE-STYLE 1)
(def YAML-FLOW-SEQUENCE-STYLE  2)
(def YAML-BLOCK-MAPPING-STYLE  1)
(def YAML-FLOW-MAPPING-STYLE   2)

;; --- struct field offsets from dev/offsetof.c --------------------------------

(def ^:private EVENT-TYPE-OFFSET      0)
(def ^:private EVENT-DATA-OFFSET      8)
(def EVENT-START-MARK       56)
(def EVENT-END-MARK         80)

(def ^:private DATA-SCALAR-ANCHOR      0)
(def ^:private DATA-SCALAR-TAG         8)
(def ^:private DATA-SCALAR-VALUE      16)
(def ^:private DATA-SCALAR-LENGTH     24)
(def ^:private DATA-SCALAR-PLAIN-IMPLICIT  32)
(def ^:private DATA-SCALAR-QUOTED-IMPLICIT 36)
(def ^:private DATA-SCALAR-STYLE      40)

(def ^:private DATA-ANCHOR             0)  ;; alias, sequence_start, mapping_start

(def ^:private DATA-TAG                8)  ;; sequence_start, mapping_start
(def ^:private DATA-IMPLICIT          16)  ;; sequence_start, mapping_start
(def ^:private DATA-STYLE             20)  ;; sequence_start, mapping_start

(def ^:private MARK-INDEX   0)
(def ^:private MARK-LINE    8)
(def ^:private MARK-COLUMN 16)

;; --- parser lifecycle --------------------------------------------------------

(defn create-parser
  "Allocate and initialize a yaml_parser_t. Returns the pointer, or nil on failure."
  []
  (let [ptr (ffi/alloc 480)]        ;; sizeof(yaml_parser_t)
    (if (zero? (yaml-parser-initialize ptr))
      (do (ffi/free ptr) nil)
      ptr)))

(defn destroy-parser
  "Destroy a parser previously returned by create-parser."
  [parser]
  (yaml-parser-delete parser)
  (ffi/free parser)
  nil)

(defn set-input-string
  "Point parser at a UTF-8 byte array of the given length."
  [parser buf len]
  (yaml-parser-set-input-string parser buf len))

;; --- event lifecycle ---------------------------------------------------------

(defn alloc-event
  "Allocate a yaml_event_t (104 bytes)."
  []
  (ffi/alloc 104))

(defn free-event
  "Call yaml_event_delete and free the event buffer."
  [event]
  (yaml-event-delete event)
  (ffi/free event)
  nil)

(defn parse-next
  "Parse the next event from parser into event. Returns true on success."
  [parser event]
  (pos? (yaml-parser-parse parser event)))

;; --- event field readers -----------------------------------------------------

(defn event-type
  "Read the event's type integer."
  [event]
  (ffi/read event :int EVENT-TYPE-OFFSET))

(defn event-start-mark
  "Read start mark as {:index :line :column}."
  [event]
  (let [base EVENT-START-MARK]
    {:index  (ffi/read event :size_t (+ base MARK-INDEX))
     :line   (ffi/read event :size_t (+ base MARK-LINE))
     :column (ffi/read event :size_t (+ base MARK-COLUMN))}))

(defn event-end-mark
  "Read end mark as {:index :line :column}."
  [event]
  (let [base EVENT-END-MARK]
    {:index  (ffi/read event :size_t (+ base MARK-INDEX))
     :line   (ffi/read event :size_t (+ base MARK-LINE))
     :column (ffi/read event :size_t (+ base MARK-COLUMN))}))

(defn event-marks
  "Read both marks as {:start {:index :line :column} :end ...}."
  [event]
  {:start (event-start-mark event)
   :end   (event-end-mark event)})

(defn- data-offset
  "Offset into the data union for a given sub-struct field."
  [field]
  (+ EVENT-DATA-OFFSET field))

;; --- scalar event readers ----------------------------------------------------

(defn scalar-anchor
  "Read the anchor string from a SCALAR event, or nil."
  [event]
  (let [ptr (ffi/read event :pointer (data-offset DATA-SCALAR-ANCHOR))]
    (when-not (ffi/null? ptr) (ffi/ptr->string ptr))))

(defn scalar-tag
  "Read the tag string from a SCALAR event, or nil."
  [event]
  (let [ptr (ffi/read event :pointer (data-offset DATA-SCALAR-TAG))]
    (when-not (ffi/null? ptr) (ffi/ptr->string ptr))))

(defn scalar-value
  "Read the scalar value string from a SCALAR event, or nil."
  [event]
  (let [ptr (ffi/read event :pointer (data-offset DATA-SCALAR-VALUE))]
    (when-not (ffi/null? ptr) (ffi/ptr->string ptr))))

(defn scalar-length
  "Read the scalar value's byte length."
  [event]
  (ffi/read event :size_t (data-offset DATA-SCALAR-LENGTH)))

(defn scalar-plain-implicit
  "Read the plain-implicit flag."
  [event]
  (ffi/read event :int (data-offset DATA-SCALAR-PLAIN-IMPLICIT)))

(defn scalar-quoted-implicit
  "Read the quoted-implicit flag."
  [event]
  (ffi/read event :int (data-offset DATA-SCALAR-QUOTED-IMPLICIT)))

(defn scalar-style
  "Read the scalar style."
  [event]
  (ffi/read event :int (data-offset DATA-SCALAR-STYLE)))

(defn read-scalar
  "Read all scalar fields at once, returning {:anchor :tag :value :length :plain-implicit :quoted-implicit :style}."
  [event]
  {:anchor         (scalar-anchor event)
   :tag            (scalar-tag event)
   :value          (scalar-value event)
   :length         (scalar-length event)
   :plain-implicit (scalar-plain-implicit event)
   :quoted-implicit (scalar-quoted-implicit event)
   :style          (scalar-style event)})

;; --- alias event readers -----------------------------------------------------

(defn alias-anchor
  "Read the anchor from an ALIAS event, or nil."
  [event]
  (let [ptr (ffi/read event :pointer (data-offset DATA-ANCHOR))]
    (when-not (ffi/null? ptr) (ffi/ptr->string ptr))))

;; --- sequence_start / mapping_start event readers ----------------------------

(defn collection-anchor
  "Read anchor from a SEQUENCE-START or MAPPING-START event."
  [event]
  (let [ptr (ffi/read event :pointer (data-offset DATA-ANCHOR))]
    (when-not (ffi/null? ptr) (ffi/ptr->string ptr))))

(defn collection-tag
  "Read tag from a SEQUENCE-START or MAPPING-START event."
  [event]
  (let [ptr (ffi/read event :pointer (data-offset DATA-TAG))]
    (when-not (ffi/null? ptr) (ffi/ptr->string ptr))))

(defn collection-implicit
  "Read implicit flag from a SEQUENCE-START or MAPPING-START event."
  [event]
  (ffi/read event :int (data-offset DATA-IMPLICIT)))

(defn collection-style
  "Read style from a SEQUENCE-START or MAPPING-START event."
  [event]
  (ffi/read event :int (data-offset DATA-STYLE)))

(defn read-collection-start
  "Read all collection-start fields at once: {:anchor :tag :implicit :style}."
  [event]
  {:anchor   (collection-anchor event)
   :tag      (collection-tag event)
   :implicit (collection-implicit event)
   :style    (collection-style event)})
