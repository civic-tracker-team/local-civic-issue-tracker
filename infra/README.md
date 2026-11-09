# Infrastructure

This directory contains shared infrastructure configuration. `postgres/records-bootstrap.sql` provisions the records schema and roles. Model-runtime and API-routing configuration remain separate placeholders.

There is no runnable Compose setup yet. Keep developer datasets, database exports, model files and private configuration outside version control; use configurable locations rather than machine-specific paths.
