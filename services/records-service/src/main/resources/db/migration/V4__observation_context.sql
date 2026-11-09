ALTER TABLE records.agenda_item
  ADD COLUMN observed_at timestamptz,
  ADD COLUMN revision bigint NOT NULL DEFAULT 0;
ALTER TABLE records.document_context ADD CONSTRAINT document_context_identity
  UNIQUE (document_id, id);
ALTER TABLE records.source_observation
  ADD COLUMN context_id uuid,
  ADD COLUMN item_label text,
  ADD CONSTRAINT observation_context_owned
    FOREIGN KEY (document_id, context_id)
    REFERENCES records.document_context(document_id, id);
-- Earlier observations have no asserted context where it was not captured.
