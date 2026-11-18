# System design overview

Local Civic Issue Tracker helps people follow specific civic matters across San Bernardino meetings. It uses official records to provide plain-language summaries, meeting overviews and issue histories, with links back to the supporting records and available official videos.

An issue is a particular project, proposal, policy change or other continuing matter. A broad subject such as transportation is not enough to connect two records. The timeline shows how an issue developed, including proposals, decisions and later changes, rather than listing documents by upload date.

## Services

| Component | Responsibility |
|---|---|
| Records | Discover official inputs, retain originals and source relationships, and prepare readable content |
| OCR | Recognize text from selected images and return text with positions |
| Analysis | Identify supported developments and issue connections, then maintain summaries, overviews and timelines |
| Model runtime | Run language-model inference requested by analysis |
| Search | Find issues, records and meetings through records and analysis APIs |
| Pipeline coordinator | Schedule and admit runs, advance meetings in order and coordinate recovery |
| Frontend | Present the browser interface through application APIs |
| API routing | Direct browser requests to the appropriate service |

Records, search, analysis and the coordinator use Java and Spring Boot. OCR uses Python. The frontend uses React, TypeScript and Vite. MVC separates request handling from application and persistence responsibilities within the Java services. Related analysis functions stay together to avoid additional interfaces for closely connected work.

Services exchange serialized values through HTTP APIs. Their Java clients can expose typed methods, but they do not share live objects, JPA sessions or database ownership. Interactive requests remain separate from background processing. A gateway product, detailed search implementation and production language-model runtime are still open choices.

## Processing

The coordinator groups consecutive whole meetings up to a configurable meeting count. Records prepares the selected inputs before analysis begins. Official meeting date determines processing order, and each meeting's analysis finishes before the next meeting starts.

Native extraction is preferred when it recovers the required content. OCR handles ordinary scans as well as maps and graphics. Preparation retains the exact source association, text and locations needed for later evidence references. A successful download is not proof of successful extraction.

Later meetings and runs remain pending while an earlier processing failure is unresolved. Successful analysis can still report an unknown date, unavailable outcome or unsupported connection. Those findings differ from a failure to retrieve or process a required input.

Records has its own durable operations and recovery controls. Coordinator-wide admission, historical replay and publication remain planned parts of the design. A records operation is not a complete cross-service run.

## Storage and publication

Original files use persistent storage, while PostgreSQL holds their references, metadata and queryable content. Each service owns its schema. Consumers use the owner's API, and neither OCR nor the model runtime has application SQL access.

Public reads will use a publication manifest containing exact source versions and analysis revisions. The coordinator advances the published view after a bounded run succeeds. Users keep access to the previous view during processing or failure. A historical correction keeps that view until the affected later history has also been rebuilt.

## Evidence and issue history

Source evidence, developments and ongoing issues are distinct linked concepts. Both explicit and inferred connections need supporting passages. Unsupported connections remain unset, while individual records remain accessible. Categories use the agreed flat vocabulary where that feature can remain simple.

Histories use the relevant evidence currently available, including later sources that establish an earlier event. Newer publications are preferred when accounts conflict, while explicit corrections and unexplained contradictions remain distinguishable. The timeline uses a supported occurrence date where available. Otherwise, it identifies the source meeting date as the date the matter was reported or discussed. Publication dates retain their separate meaning. Approximate dates and ranges preserve their precision, and a planned date passing does not prove completion.

## Related designs

- [Records identity](RECORDS_IDENTITY.md) explains identifiers, observations and byte versions.
- [Processing and publication](T03_DESIGN.md) explains recovery, APIs and historical replay.
- [Record acquisition](T04_DESIGN.md) explains PrimeGov discovery and retrieval.
- [Record preparation](T05_DESIGN.md) explains extraction, OCR and retained content.

Individual rationales are recorded in the architecture decision records. Codex assisted with organizing these design documents and checking their consistency with the requirements and code. Kazim Yigit Ablay selected the recorded design choices.
