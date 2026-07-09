# jolt-yaml

YAML parsing and serialization for [Jolt](https://github.com/jolt-lang/jolt), bound to
the system **libyaml** through `jolt.ffi` and exposed as a clean Clojure API plus a
SnakeYAML engine v2 surface for yamlscript compatibility.

## Use it

```clojure
(require '[jolt.yaml :as yaml])

;; Parse a YAML string
(yaml/load "foo: 1\nbar: 2\n")
;; => {"foo" 1, "bar" 2}

;; Keywords opt-in
(yaml/load "hello: world\n" {:keywords true})
;; => {:hello "world"}

;; Multi-document streams
(yaml/load-all "---\na: 1\n---\nb: 2\n")
;; => ({"a" 1} {"b" 2})

;; Serialize Clojure data to YAML
(yaml/dump {:a [1 2 3]})
```

### clj-yaml compat

```clojure
(require '[clj-yaml.core :as cy])

;; keywords: true by default (matching clj-yaml)
(cy/parse-string "a: 1\n")
;; => {:a 1}

(cy/generate-string {:hello "world"})
```

### SnakeYAML surface

```clojure
(require '[jolt.yaml.snakeyaml])

(import '(org.snakeyaml.engine.v2.api LoadSettings)
        '(org.snakeyaml.engine.v2.api.lowlevel Parse))

(let [parser (new Parse (.build (LoadSettings/builder)))
      events (.parseString parser "hello\n")]
  ...)
```

## What's provided

- **`jolt.yaml`** — the public API: `load`, `load-all`, `dump` / `generate-string`.
- **`jolt.yaml.ffi`** — low-level libyaml bindings: parser lifecycle, event pump,
  struct field readers at baked offsets (C offsetof probe, ARM64 macOS 0.2.5).
- **`jolt.yaml.snakeyaml`** — `org.snakeyaml.engine.v2` classes registered as Jolt
  host objects: `LoadSettings` / `Parse` / all 10 event types with marks.
- **`clj-yaml.core`** — clj-commons/clj-yaml compatible API (`parse-string`,
  `parse-stream`, `generate-string`).

## Native dependency

libyaml is declared `:jolt/native` in `deps.edn` and loaded before the namespace.
On macOS via Homebrew at `/opt/homebrew/opt/libyaml/lib/libyaml.dylib`; on Linux
as `libyaml-0.so.2`.

## Build and test

```sh
joltc -M:test   # run the test suite
```

## deps.edn

```clojure
{:deps {io.github.jolt-lang/yaml {:git/sha "..."}}}
```
