# Local Civic Issue Tracker

Local Civic Issue Tracker is a web application project for following local issues through San Bernardino City Council records. It aims to help people find relevant information, understand how an issue develops across meetings, and check that information against its original sources.

The project focuses on selected Council records from 2023 onward, including agendas, minutes, staff reports, attachments, and existing official transcripts.

## Planned features

- Import selected official records while retaining their source information.
- Search issues, individual records and meetings without needing to know meeting dates or official terminology.
- Identify supported connections between records and distinguish source-stated relationships from system inference.
- Follow an issue's evolution across meetings in a timeline, with access to supporting evidence.
- Provide meeting overviews covering agenda topics and documented outcomes.
- Produce plain-language summaries supported by official records.
- Provide links to the original records behind the displayed information.
- Include links to official meeting videos when available.

## Engineering documents

The maintained SRS, SPMP and architecture decision records belong in [docs/engineering](docs/engineering/README.md).

SRS v1.0 is available as an [editable DOCX](docs/engineering/srs/LocalCivicIssueTracker_SRS_v1.0.docx) and [PDF](docs/engineering/srs/LocalCivicIssueTracker_SRS_v1.0.pdf).

## Development

The repository contains the initial service and frontend folder structure and template Maven configuration. See [development setup](docs/development/README.md) for the layout, selected tooling and current scaffold boundaries.
