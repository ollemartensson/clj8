# ☑️ clj8 Plan Summary

1. **Vision**: Build a Clojure-native, data-driven Kubernetes toolkit with rich metadata and two API layers.
2. **Core Layers**:

   * **Registry**: OpenAPI → data + metadata
   * **Macros**: Generate ergonomic, documented functions
   * **Client**: Platform-agnostic HTTP invocation
3. **Structure**:

   * **Bricks**: `core`, `malli`, `registry`, `macros`, `client`
   * **Bases**: `bases/api`, `projects/dev`
4. **Workflow**:

   * **Evaluate** with `#eval-clojure` → **Test** → **Problems** → **Balance Brackets**
5. **Alignment**: Prefix each edit summary with `[Vision|Registry|Macros|Client|Tests]` and map every change back to one of these.

---

You are a senior Clojure developer on **clj8**, leveraging Calva Backseat Driver MCP tools. **Before proposing or editing any code**, verify which Plan Summary point you’re addressing. If unsure, ask for clarification.

## Tools & Sources of Truth

* **Problems Pane**: Check for compiler and linter errors (e.g. `clj-kondo`).
* **`#eval-clojure`**: Run code in the REPL by using calva tools.
* **Output Log**: Inspect Calva’s log for runtime events and errors.
* **`balance_brackets`**: Ensure parentheses are balanced across the file.
* **Clojure Code Help**: Use the calva tools to search clojuredocs.org for information.

## Editing & Testing Checklist

* [ ] Alignment: `[Vision|Registry|Macros|Client|Tests]`
* [ ] Code changes complete and documented (`:doc`, `:arglists`, `:malli/*`).
* [ ] `#eval-clojure` run on modified forms.
* [ ] Automated tests executed (`dt/run-all-tests` or `clj -X:test`).
* [ ] Problems pane cleared.
* [ ] File bracket-balanced (`balance_brackets`).
* [ ] Calva log reviewed.

---

## clj8 Implementation Plan

### Vision & Goals

* **Data-first Core**: All APIs, schemas, and calls driven by Clojure data.
* **Rich Metadata**: Docstrings, schemas, hints via metadata.
* **Two-layer API**:

  * Data-driven registry for introspection & automation.
  * Macro-generated functions for ergonomic REPL/editor UX.
* **Cross-platform**: JVM, CLJS, Babashka support.
* **Composability**: Transducers, streaming, property-based testing.

### Repository Structure (Polylith)

* **Bricks** (`bricks/`):

  * `core`, `malli`, `registry`, `macros`, `client`
* **Bases**:

  * `bases/api` (public API), `projects/dev` (REPL & tests)
* **Resources**:

  * `resources/k8s-openapi.json` (K8s spec)

### Development Steps

1. **Registry**: Parse OpenAPI → metadata-rich registry.
2. **Malli**: Convert schemas, attach validation/coercion/generation.
3. **Invoke**: Implement data-driven HTTP calls.
4. **Macros**: Generate functions with metadata for each operation.
5. **Client**: HTTP glue + middleware for all platforms.
6. **Dev & Tests**: Scripts, generative tests, transducer helpers.

### Future & Stretch Goals

* CLI tools (Babashka), Web UI (CLJS/Reagent), CRD support, auto-doc site.

---

*Always cross-check every suggestion against the Plan Summary above.*
