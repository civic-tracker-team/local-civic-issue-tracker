# ADR-0006: Store original files separately from SQL data

- **Status:** Accepted
- **Date:** 2026-10-29
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-07, DR-02, DR-03, DR-06

## Context

Imported official documents must remain identifiable after extraction and analysis. The application also needs queryable metadata, readable content and derived information. These responsibilities do not require the original bytes and every queryable value to use the same storage mechanism.

Keeping the source original matters because extracted text or a summary may omit information. Retained file artifacts also need durable storage, while application data needs associations that services can query.

## Decision

We will retain original document bytes and selected file artifacts in persistent file storage. SQL will hold file references, identifying metadata and queryable application data. Records will retain prepared readable content associated with its original source.

The original remains distinct from the readable text and analysis derived from it. Application file identities must remain usable across configured local storage locations rather than depending on one developer's directory.

This decision does not choose a storage provider, directory/key convention, mount technology or requirement to retain every intermediate output. Service ownership and access are addressed separately.

## Alternatives considered

The storage discussion compared retaining files separately with keeping file bytes in database storage. The file/SQL separation was selected for the intended workflow. No measured claim about database BLOB performance, cost or capacity is made.

Retaining only extracted text would not satisfy the requirement to preserve original bytes. A stored summary is therefore not a replacement for the original record.

## Consequences

- **Positive:** Originals remain available independently of extraction quality, while SQL supports queries and associations.
- **Negative:** File retention and database references must remain consistent. Copying or restoring only one side can leave an unusable dataset.
- **Follow-ups:** Define storage access, retention and consistency procedures. Persistent storage must survive the lifecycle of disposable service processes. The exact mechanism remains open, and snapshot exchange is specified separately in ADR-0012.

## AI assistance and provenance

Codex helped organize the storage workflow and check it against the SRS and accepted file/SQL decision. Kazim selected this separation. The consequences describe design responsibilities, not a tested storage implementation.

## References

- SRS v1.0
- ADR-0008: Service data ownership
- ADR-0012: Dataset snapshots
