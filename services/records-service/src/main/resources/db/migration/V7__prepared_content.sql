ALTER TABLE records.record_operation DROP CONSTRAINT record_operation_kind_check;
ALTER TABLE records.record_operation ADD CONSTRAINT record_operation_kind_check
  CHECK (kind IN ('DISCOVERY', 'ACQUISITION', 'PREPARATION'));

ALTER TABLE records.original_version ADD CONSTRAINT version_artifact_identity UNIQUE(id,artifact_id);
CREATE TABLE records.prepared_content (
  id uuid PRIMARY KEY,
  document_id uuid NOT NULL,
  version_id uuid NOT NULL,
  artifact_id uuid NOT NULL REFERENCES records.file_artifact,
  input_sha256 varchar(64) NOT NULL,
  profile_fingerprint varchar(64) NOT NULL,
  profile_json text NOT NULL,
  unit_count integer CHECK (unit_count > 0),
  sealed_at timestamptz,
  FOREIGN KEY(document_id, version_id) REFERENCES records.original_version(document_id,id),
  FOREIGN KEY(version_id,artifact_id) REFERENCES records.original_version(id,artifact_id),
  CHECK ((sealed_at IS NULL) OR (unit_count IS NOT NULL))
);
CREATE INDEX reusable_prepared_content
  ON records.prepared_content(version_id,artifact_id,profile_fingerprint) WHERE sealed_at IS NOT NULL;
CREATE TABLE records.preparation_entry (
  id uuid PRIMARY KEY,
  operation_id uuid NOT NULL REFERENCES records.record_operation,
  ordinal integer NOT NULL CHECK (ordinal >= 0),
  acquisition_item_id uuid NOT NULL REFERENCES records.acquisition_item,
  preparation_id uuid REFERENCES records.prepared_content,
  UNIQUE(operation_id,ordinal), UNIQUE(operation_id,acquisition_item_id)
);
CREATE TABLE records.prepared_unit (
  id uuid PRIMARY KEY,
  preparation_id uuid NOT NULL REFERENCES records.prepared_content,
  ordinal integer NOT NULL CHECK (ordinal >= 0),
  disposition text NOT NULL CHECK (disposition IN ('NATIVE','OCR','EMPTY','NON_TEXT')),
  native_text text NOT NULL,
  notes_json text NOT NULL,
  UNIQUE(preparation_id,ordinal)
);
CREATE TABLE records.prepared_segment (
  id uuid PRIMARY KEY,
  unit_id uuid NOT NULL REFERENCES records.prepared_unit,
  ordinal integer NOT NULL CHECK (ordinal >= 0),
  text_value text NOT NULL CHECK (length(text_value)>0),
  method text NOT NULL CHECK (method IN ('HTML_NATIVE','PDF_NATIVE','OCR')),
  locator_json text NOT NULL,
  score double precision CHECK (score >= 0 AND score <= 1),
  selected boolean NOT NULL,
  UNIQUE(unit_id,ordinal)
);
CREATE TABLE records.preparation_ocr (
  id uuid PRIMARY KEY,
  operation_id uuid NOT NULL REFERENCES records.record_operation,
  attempt_id uuid NOT NULL,
  preparation_id uuid NOT NULL REFERENCES records.prepared_content,
  unit_ordinal integer NOT NULL CHECK (unit_ordinal >= 0),
  image_ordinal integer NOT NULL CHECK (image_ordinal >= 0),
  image_sha256 varchar(64) NOT NULL,
  image_key text NOT NULL,
  state text NOT NULL CHECK (state IN ('STARTED','RETURNED','STOP_CONFIRMED')),
  result_json text,
  CHECK ((result_json IS NULL) OR state='RETURNED')
);
ALTER TABLE records.record_operation ADD COLUMN blocked_execution_id uuid
  REFERENCES records.preparation_ocr;
ALTER TABLE records.record_operation ADD COLUMN preparing_version_id uuid REFERENCES records.original_version,
  ADD COLUMN preparing_unit_ordinal integer CHECK (preparing_unit_ordinal >= 0);

GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE records.prepared_content,
  records.preparation_entry,records.prepared_unit,records.prepared_segment,
  records.preparation_ocr TO "${recordsRuntimeRole}";
