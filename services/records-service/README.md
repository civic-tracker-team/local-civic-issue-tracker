# Records service

Java package: `org.civictracker.records`.

Owns official-source identities and relationships, retained files, prepared readable content and their metadata. Requests OCR when native extraction is insufficient and persists the returned result. Other services obtain records data through this service's APIs.

The application layer retains supplied original bytes and source metadata in PostgreSQL and configured file storage. It preserves internal identities, official-ID history, document relationships and byte versions. Flyway manages the records schema; JPA validates it at startup.

`RecordsFoundation` provides retention, metadata inspection and current or historical original-byte retrieval. Authenticated internal document, version and artifact-byte reads form the first HTTP slice. Durable discovery and acquisition operations now connect the configured PrimeGov source to this foundation. Prepared text and OCR remain later increments.

Database configuration and verification are described in [records persistence setup](../../docs/development/RECORDS_PERSISTENCE.md). The accepted contracts are in `docs/engineering/design/T03_DESIGN.md`.

The [internal read contract and configuration](../../docs/development/RECORDS_READS.md) describes original/version reads and transport requirements. The [acquisition setup](../../docs/development/RECORDS_ACQUISITION.md) covers source profiles, worker configuration, processing permissions and recovery.
