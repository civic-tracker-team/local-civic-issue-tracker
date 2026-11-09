-- A document's initial meeting is not an exclusive provenance parent.
-- Explicitly retained contexts may associate it with another meeting/item.
-- Application matching still applies the approved source-context scope.
ALTER TABLE records.document_context DROP CONSTRAINT context_document_meeting;
ALTER TABLE records.source_alias DROP CONSTRAINT alias_document_meeting;
