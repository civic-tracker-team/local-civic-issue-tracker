# Processing and publication

The processing design separates durable service work from the public view. Records manages acquisition and preparation; analysis manages issue data and derived results. The coordinator controls the order of that work and publication after completion.

## Data and API boundaries

Each service owns its data and exposes data transfer objects through APIs. Metadata responses contain identifiers, source information and relationships. Original bytes and prepared text are fetched separately for an exact retained version. Prepared references also identify the extraction revision, so consumers can keep citations attached to unchanged text.

Collection requests use bounded `page` and `size` parameters with a fixed selection. Records meeting collections currently use a successful discovery ID. Their date-and-UUID ordering is deterministic; the UUID tie-breaker does not imply an order between simultaneous civic events. Current route details are in the developer guides.

Source locations use PDF physical pages or HTML item and section identities. Text offsets count Unicode code points from zero, with an exclusive end, against the exact retained segment. Unknown metadata stays unknown rather than receiving a fabricated date or location.

Internal routes require service authentication and remain outside public routing. The planned `/v1` reads will additionally enforce membership in a published collection. Those public APIs and the coordinator's publication storage are not yet implemented.

## Durable operations

A service saves the operation UUID and fixed inputs before returning HTTP 202 and a status URL. Equivalent submissions return that operation. Reusing the UUID with different inputs returns a conflict. States distinguish acceptance, execution, success and failure, and success follows durable storage of usable output.

After a timeout, the coordinator checks the original operation instead of creating another one. A timeout is not evidence that remote work ended. HTTP errors follow RFC 9457 with stable application codes and correlation references, without exposing secrets or physical locations.

Records permits one local owner operation at a time through a persisted database slot. Execution-attempt checks protect checkpoint commits. The coordinator needs a separate database-controlled active-run record to prevent overlapping cross-service runs, including admission while an earlier failure remains unresolved. Spring Batch remains conditional on verification of that behavior.

## Meeting progression and recovery

Runs contain consecutive whole meetings with a configurable count limit. Records prepares the run's inputs first. Analysis then processes meetings by official meeting date and completes each meeting's saved updates before advancing. A meeting count does not bound document pages, model context or runtime. Whole-meeting capacity still needs representative evaluation.

A processing failure keeps later work pending. Maintainers can inspect and correct the problem, then resume after confirming that the earlier execution has stopped. Automatic skips and timed takeover are not part of the initial recovery approach. Records already supports explicit recovery of its own operations, including the additional remote-inference checks used by OCR.

## Historical corrections

A changed effective input can affect the analysis of later meetings. Replay starts at the earliest affected meeting, using a verified analysis state from before that boundary. If that state is unavailable, rebuilding begins at the start of the selected retained collection. Superseded source versions and their associations remain available.

A new portal alias with identical effective inputs does not, by itself, require another analysis. The design does not yet provide dependency-based selective replay. Chronological replay and coordinator-wide recovery still need implementation and integration tests.

## Publication

The coordinator's publication manifest will identify exact source versions and analysis revisions. Services save those outputs before acknowledging completion. After verifying the complete manifest, one coordinator transaction advances the current-publication pointer. This is not a transaction spanning every service.

Public clients retain a publication ID across related reads and pages. Owners check that the requested objects belong to that view. Before the first publication, the application reports that no published collection exists rather than implying the official portal has no records.

Normal new-input runs publish after each successful bounded run. Historical correction work keeps the prior publication until the affected later history is coherent, even when replay spans several runs. A corrected prefix must not appear alongside unrecomputed later results. ADR-0017 records this visibility decision; ADR-0028 records recovery and replay.
