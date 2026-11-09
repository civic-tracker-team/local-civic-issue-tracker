# Analysis service

Java package: `org.civictracker.analysis`.

Owns source evidence, developments, ongoing issues and derived results. Linking, summaries, meeting overviews and issue timeline construction stay together here. Reads prepared records through the records API and invokes the separate model runtime.

The Spring Boot entry point starts the HTTP baseline, with a real-server startup test. Domain persistence, model invocation, analysis and record clients are not implemented. Production model selection and inference contracts remain open.
