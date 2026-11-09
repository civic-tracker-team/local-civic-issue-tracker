# Records service

Java package: `org.civictracker.records`.

Owns official-source identities and relationships, retained files, prepared readable content and their metadata. Requests OCR when native extraction is insufficient and persists the returned result. Other services obtain records data through this service's APIs.

The application layer retains supplied original bytes and source metadata in PostgreSQL and configured file storage. It preserves internal identities, official-ID history, document relationships and byte versions. Flyway manages the records schema; JPA validates it at startup.

`RecordsFoundation` provides retention, metadata inspection and current or historical original-byte retrieval. It has no domain HTTP endpoints yet. Portal discovery, acquisition, prepared text and OCR remain separate increments.

Database configuration and verification are described in [records persistence setup](../../docs/development/RECORDS_PERSISTENCE.md). The accepted contracts are in `docs/engineering/design/T03_DESIGN.md`.
