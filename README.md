# clj8

![clj8 Logo](resources/clj8-logo.png)

Clojure-native, data-driven Kubernetes.

- `bricks/` are components
- `bases/api` is a macro-generated API base
- `projects/dev` for REPL/dev/testing
- See `resources/` for k8s-openapi.json

## Usage

1. Download the K8s OpenAPI spec to `resources/k8s-openapi.json`
2. Start REPL in `projects/dev` (or `clj -A:dev`)
3. Develop, test, and publish modular bricks or full systems

See [polylith docs](https://polylith.gitbook.io/polylith/) for monorepo workflow.