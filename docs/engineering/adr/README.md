# Architecture decision records

Each record explains one architectural choice, its context, alternatives and consequences. The decision date identifies the original choice, while the recorded date identifies when the note was written. Accepted status records a chosen design, not a claim that every related feature has been implemented or verified.

Records name the requirements version they use. Later records can refine an earlier choice without rewriting its history.

| ADR | Decision | Decision date |
|---|---|---|
| ADR-0002 | Refine the service boundaries around records and analysis | 2026-10-26 |
| ADR-0003 | Use Java/Spring Boot application services and Python OCR | 2026-10-27 |
| ADR-0004 | Use a Maven parent and separate Java service modules | 2026-10-26 |
| ADR-0005 | Use React, TypeScript and Vite for the frontend | 2026-10-29 |
| ADR-0006 | Store original files separately from SQL data | 2026-10-29 |
| ADR-0007 | Use one local PostgreSQL database with service-owned schemas | 2026-10-29 |
| ADR-0008 | Keep domain ownership behind service APIs | 2026-10-29 |
| ADR-0009 | Keep inference database-free and persist through the owner | 2026-10-29 |
| ADR-0010 | Use JPA-first persistence for records and analysis | 2026-10-29 |
| ADR-0011 | Use HTTP/JSON with typed Spring service clients | 2026-10-29 |
| ADR-0012 | Share coordinated datasets between local development instances | 2026-10-29 |
| ADR-0013 | Prefer native PDF extraction and use OCR when needed | 2026-10-08 |
| ADR-0014 | Preserve portal-scoped meeting identity and record relationships | 2026-10-27 |
| ADR-0015 | Coordinate durable, non-overlapping chronological analysis | 2026-10-26 |
| ADR-0016 | Prepare count-bounded whole-meeting runs before analysis | 2026-10-27 |
| ADR-0017 | Publish completed runs and retain the previous published view | 2026-10-30 |
| ADR-0018 | Separate source evidence, developments and ongoing issues | 2026-10-26 |
| ADR-0019 | Maintain current reconstructed history with source versions | 2026-10-26 |
| ADR-0020 | Preserve temporal meaning and uncertainty | 2026-10-29 |
| ADR-0021 | Keep imported content outside application authority | 2026-10-30 |
| ADR-0022 | Assign internal UUIDv4 identifiers | 2026-11-08 |
| ADR-0023 | Match portal identifiers within their source context | 2026-11-08 |
| ADR-0024 | Use an exact-URL fallback when official IDs are unavailable | 2026-11-08 |
| ADR-0025 | Retain byte versions separately from source observations | 2026-11-08 |
| ADR-0026 | Separate metadata, prepared text and original downloads | 2026-11-08 |
| ADR-0027 | Save processing operations before executing their work | 2026-11-08 |
| ADR-0028 | Replay corrected history from the earliest affected meeting | 2026-11-08 |
| ADR-0029 | Use owner-managed Flyway migrations and restricted runtime roles | 2026-11-08 |
| ADR-0030 | Install complete files before committing their metadata | 2026-11-08 |
| ADR-0031 | Configure separate internal read, processing and maintenance permissions | 2026-11-08 |
| ADR-0032 | Acquire PrimeGov originals from fixed meeting selections | 2026-11-13 |
| ADR-0033 | Restrict and bound outbound portal requests | 2026-11-13 |
| ADR-0034 | Check content coverage before accepting native extraction | 2026-11-13 |
| ADR-0035 | Use a pinned local PP-OCRv6 medium runtime | 2026-11-13 |
| ADR-0036 | Retain immutable prepared text with exact source locations | 2026-11-13 |
| ADR-0037 | Keep OCR synchronous with caller-owned recovery | 2026-11-13 |
