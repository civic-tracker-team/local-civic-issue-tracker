# Analysis service

Java package: `org.civictracker.analysis`.

Analysis owns source evidence, developments, ongoing issues and derived results. Linking, summaries, meeting overviews and issue timelines stay together here.

The implemented T06 path is a developer evaluation CLI. It reads frozen prepared records through an authenticated records client, invokes the local llama.cpp adapter, resolves citations and validates candidates. The JAR also has offline `summarize` diagnostics and a `score` command requiring approved references and review judgments. Normal startup runs the Spring HTTP baseline.

The frozen contract is `grounded-findings-3`. Production analysis routes, civic-domain persistence, publication and derived views are not implemented. Production model and runtime selection remain open.

See the [evaluation guide](../../docs/development/MODEL_EVALUATION.md) for commands and the [T06 design](../../docs/engineering/design/T06_DESIGN.md) for the contract and known limitations.
