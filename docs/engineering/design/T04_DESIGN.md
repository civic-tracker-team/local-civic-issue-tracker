# Record acquisition design

Discovery and acquisition belong to records. The PrimeGov adapter interprets official listings and agenda structure, while the persistence code maintains document identities, source observations and original files.

## Source selection

Discovery captures archived and upcoming City Council listings for configured dates and committees. The current adapter handles available English-labelled generated HTML and PDF records, supported PDF item attachments, cancellation notices and supplied official video links. Transcript references can use supported document forms, but discovery of every official transcript format is not established.

A discovery result fixes the selected meetings, source metadata and profile. Acquisition handles one selected meeting and revalidates its listing before downloading. A changed selection requires replacement work. The portal is not a transactional snapshot, so individual downloads retain their own observation times.

Published expected links must be retrieved successfully. Document types that have not been published are not invented as mandatory inputs. A meeting can therefore be retained without an original file. Official cancellation evidence preserves its identity without turning agenda proposals into meeting outcomes.

## Import flow

The caller submits an authenticated command. Records saves acceptance, then its worker downloads each selected original outside a SQL transaction. Files are staged and verified before a short transaction records the original version, observation and completed-item receipt.

Agenda HTML supplies item and attachment relationships. Packet expansion is saved with the packet's receipt, so resume can use the same attachment selection. Complete items are reused; incomplete transfers restart. The adapter follows supported attachment-history links rather than arbitrary hyperlinks in imported text.

Rescans retrieve selected originals and compare SHA-256. Stable portal IDs or publication fields do not prove unchanged bytes. Receipts retain the exact imported version even when another version remains the document's current selection. Raw HTML token changes remain byte changes without automatically becoming civic changes.

## Execution and access

A durable slot permits one records operation at a time. It survives a crash, and checkpoint transactions reject older execution attempts. A maintainer confirms that a previous execution stopped before resume. A changed input uses a linked replacement operation rather than changing a saved command.

The adapter uses the JDK HTTP client, Jackson and jsoup. Approved HTTPS hosts, redirect validation, request pacing, deadlines and size limits constrain source access. Internal credentials are never sent to the portal. Read-only, processing and maintenance permissions remain separate.

The coordinator owns scheduled runs, cross-service admission and publication. The records worker does not authorize later meeting analysis merely because an acquisition finished. Developer configuration, routes and recovery commands are in the records acquisition guide. ADR-0027 and ADR-0032 through ADR-0033 explain the main decisions.
