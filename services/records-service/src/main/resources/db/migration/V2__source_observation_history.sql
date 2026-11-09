ALTER TABLE records.source_meeting
  ADD COLUMN observed_at timestamptz,
  ADD COLUMN revision bigint NOT NULL DEFAULT 0;
ALTER TABLE records.source_observation
  ADD COLUMN meeting_title text,
  ADD COLUMN meeting_date text,
  ADD COLUMN cancellation_evidence text,
  ADD COLUMN original_filename text;
ALTER TABLE records.source_observation ADD CONSTRAINT observed_version_owned
  FOREIGN KEY (document_id, version_id)
  REFERENCES records.original_version(document_id, id);

ALTER TABLE records.source_document ADD CONSTRAINT document_meeting_identity
  UNIQUE (id, meeting_id);
ALTER TABLE records.agenda_item ADD CONSTRAINT item_meeting_identity
  UNIQUE (id, meeting_id);
ALTER TABLE records.document_context ADD CONSTRAINT context_document_meeting
  FOREIGN KEY (document_id, meeting_id)
  REFERENCES records.source_document(id, meeting_id);
ALTER TABLE records.document_context ADD CONSTRAINT context_item_meeting
  FOREIGN KEY (item_id, meeting_id)
  REFERENCES records.agenda_item(id, meeting_id);
ALTER TABLE records.source_alias ADD CONSTRAINT alias_document_meeting
  FOREIGN KEY (document_id, meeting_id)
  REFERENCES records.source_document(id, meeting_id);
