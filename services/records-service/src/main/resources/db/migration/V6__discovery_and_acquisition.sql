ALTER TABLE records.source_meeting
  ADD COLUMN committee_id text,
  ADD COLUMN local_date date,
  ADD COLUMN publication_date text,
  ADD COLUMN video_url text;
ALTER TABLE records.source_observation
  ADD COLUMN publication_date text,
  ADD COLUMN resolved_url text;

CREATE TABLE records.record_operation (
  id uuid PRIMARY KEY,
  kind text NOT NULL CHECK (kind IN ('DISCOVERY', 'ACQUISITION')),
  state text NOT NULL CHECK (state IN ('ACCEPTED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
  input_json text NOT NULL,
  accepted_at timestamptz NOT NULL, started_at timestamptz, completed_at timestamptz,
  attempt_id uuid, worker_id uuid,
  replaces_id uuid REFERENCES records.record_operation,
  error_code text, attempts integer NOT NULL DEFAULT 0,
  CHECK ((state IN ('SUCCEEDED', 'FAILED')) = (completed_at IS NOT NULL)),
  CHECK ((state = 'FAILED') = (error_code IS NOT NULL))
);
CREATE INDEX accepted_record_operations ON records.record_operation(accepted_at, id)
  WHERE state = 'ACCEPTED';
CREATE TABLE records.operation_slot (
  id integer PRIMARY KEY CHECK (id = 1),
  operation_id uuid UNIQUE REFERENCES records.record_operation,
  attempt_id uuid, worker_id uuid,
  CHECK ((operation_id IS NULL) = (attempt_id IS NULL)),
  CHECK ((operation_id IS NULL) = (worker_id IS NULL))
);
INSERT INTO records.operation_slot(id) VALUES (1);
CREATE TABLE records.operation_event (
  id uuid PRIMARY KEY, operation_id uuid NOT NULL REFERENCES records.record_operation,
  state text NOT NULL, attempt_id uuid, worker_id uuid,
  recorded_at timestamptz NOT NULL, detail text
);
CREATE TABLE records.operation_capture (
  id uuid PRIMARY KEY, operation_id uuid NOT NULL REFERENCES records.record_operation,
  source_url text NOT NULL, resolved_url text NOT NULL, observed_at timestamptz NOT NULL,
  sha256 text NOT NULL, byte_length bigint NOT NULL, storage_key text NOT NULL UNIQUE
);
CREATE TABLE records.discovered_meeting (
  id uuid PRIMARY KEY, operation_id uuid NOT NULL REFERENCES records.record_operation,
  meeting_id uuid NOT NULL REFERENCES records.source_meeting,
  ordinal integer NOT NULL, payload text NOT NULL, observed_at timestamptz NOT NULL,
  UNIQUE(operation_id, meeting_id), UNIQUE(operation_id, ordinal)
);
CREATE TABLE records.acquisition_item (
  id uuid PRIMARY KEY, operation_id uuid NOT NULL REFERENCES records.record_operation,
  ordinal integer NOT NULL, target_json text NOT NULL, target_key varchar(64) NOT NULL,
  document_id uuid REFERENCES records.source_document,
  version_id uuid, artifact_id uuid REFERENCES records.file_artifact,
  observation_id uuid REFERENCES records.source_observation,
  current_version_id uuid REFERENCES records.original_version,
  sha256 text, byte_length bigint, current_changed boolean,
  UNIQUE(operation_id, ordinal), UNIQUE(operation_id, target_key),
  FOREIGN KEY(document_id, version_id) REFERENCES records.original_version(document_id, id),
  CHECK ((version_id IS NULL AND document_id IS NULL AND artifact_id IS NULL
    AND observation_id IS NULL AND current_version_id IS NULL AND sha256 IS NULL
    AND byte_length IS NULL AND current_changed IS NULL)
    OR (version_id IS NOT NULL AND document_id IS NOT NULL AND artifact_id IS NOT NULL
    AND observation_id IS NOT NULL AND current_version_id IS NOT NULL AND sha256 IS NOT NULL
    AND byte_length IS NOT NULL AND current_changed IS NOT NULL))
);
CREATE TABLE records.operation_note (
  operation_id uuid NOT NULL REFERENCES records.record_operation,
  code text NOT NULL, PRIMARY KEY(operation_id, code)
);
CREATE TABLE records.portal_request_gate (
  host text PRIMARY KEY, next_allowed_at timestamptz NOT NULL
);
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE records.record_operation,
  records.operation_slot, records.operation_event, records.operation_capture,
  records.discovered_meeting, records.acquisition_item, records.operation_note,
  records.portal_request_gate
  TO "${recordsRuntimeRole}";
