GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
  records.source_meeting, records.agenda_item, records.source_document,
  records.document_context, records.file_artifact, records.original_version,
  records.source_alias, records.source_observation
  TO "${recordsRuntimeRole}";
REVOKE ALL ON TABLE records.flyway_schema_history FROM "${recordsRuntimeRole}";
