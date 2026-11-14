# Repository working rules

- Keep private working notes, course reference materials, presentations not selected for the shared engineering set, imported records, datasets, experiment outputs, and archived prototypes in the ignored `.local` directory. Maintained project engineering documents belong in tracked `docs/engineering/`.
- Deliberately selected project code, code-related documentation, configuration, and maintained engineering documents (including SRS, SPMP, and ADRs) belong outside `.local` for version control. Keep the engineering set in `docs/engineering/`; do not copy private research, raw inputs, or course handouts there automatically. Archived prototypes are not an approved production baseline.
- When local history is relevant and available, read `.local/START_HERE.md` and `.local/CONTINUATION.md`. Local working context does not replace the current SRS or confirmed team decisions. Ask before introducing unagreed requirements, assumptions, dependencies, numerical targets, or workflows.
- Do not put machine-specific local file locations in authored documents. Configure code locations through arguments, configuration, or environment settings; avoid hardcoded absolute paths.

## Documentation and implementation context

- Human-facing setup and API guides belong in `docs/development/`. Maintained design explanations and ADRs belong in `docs/engineering/`. Keep prompts, implementation checklists, temporary test totals and session notes in `.local`.
- Read `docs/engineering/design/DESIGN_OVERVIEW.md` for service ownership. Use `RECORDS_IDENTITY.md` for matching and version rules, and the records developer guides for implemented routes. Design documents also describe planned coordinator behavior, so verify implementation before claiming it exists.
- For implementation work, plan from the relevant requirements and ADRs, implement a bounded change, then review and test it. Confirmed defects need targeted regression tests.
- Run Java checks with `tools/verify-java.ps1` against its isolated PostgreSQL database. OCR checks use `tools/verify-ocr.sh` with `OCR_PYTHON` set to the selected environment. Never run destructive test fixtures against a shared dataset.
- ADRs record focused decisions with their original dates, context, alternatives, consequences, related requirements and AI assistance. Do not invent approval, implementation or verification claims. Preserve required provenance when revising prose.
- New records persistence entities follow the existing `AssignedEntity` UUID and new-state pattern. Keep entity serialization out of APIs and preserve the migration/runtime permission boundary.
- Extraction changes that alter retained text, routing or locations need a profile or pipeline revision change. Earlier sealed references remain immutable.
