CREATE TABLE records.source_meeting (
  id uuid PRIMARY KEY, portal text NOT NULL, official_id text NOT NULL,
  title text, source_date text, cancellation_evidence text,
  UNIQUE (portal, official_id)
);
CREATE TABLE records.agenda_item (
  id uuid PRIMARY KEY, meeting_id uuid NOT NULL REFERENCES records.source_meeting,
  official_id text NOT NULL, label text, UNIQUE (meeting_id, official_id)
);
CREATE TABLE records.source_document (
  id uuid PRIMARY KEY, meeting_id uuid NOT NULL REFERENCES records.source_meeting,
  current_version_id uuid, current_observed_at timestamptz, revision bigint NOT NULL
);
CREATE TABLE records.document_context (
  id uuid PRIMARY KEY, document_id uuid NOT NULL REFERENCES records.source_document,
  meeting_id uuid NOT NULL REFERENCES records.source_meeting,
  item_id uuid REFERENCES records.agenda_item, template_id text, output_type text, language text,
  UNIQUE NULLS NOT DISTINCT (document_id, meeting_id, item_id, template_id, output_type, language)
);
CREATE TABLE records.file_artifact (
  id uuid PRIMARY KEY, storage_key text NOT NULL UNIQUE, original_filename text
);
CREATE TABLE records.original_version (
  id uuid PRIMARY KEY, document_id uuid NOT NULL REFERENCES records.source_document,
  sha256 varchar(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
  byte_length bigint NOT NULL CHECK (byte_length >= 0),
  artifact_id uuid NOT NULL REFERENCES records.file_artifact,
  UNIQUE (document_id, sha256), UNIQUE (document_id, id)
);
ALTER TABLE records.source_document ADD CONSTRAINT current_version_owned
  FOREIGN KEY (id, current_version_id) REFERENCES records.original_version(document_id, id);
CREATE TABLE records.source_alias (
  id uuid PRIMARY KEY, document_id uuid NOT NULL REFERENCES records.source_document,
  meeting_id uuid NOT NULL REFERENCES records.source_meeting,
  family text NOT NULL, value text NOT NULL, primary_reference boolean NOT NULL,
  UNIQUE (meeting_id, family, value)
);
CREATE UNIQUE INDEX one_primary_source_alias ON records.source_alias(document_id)
  WHERE primary_reference;
CREATE TABLE records.source_observation (
  id uuid PRIMARY KEY, document_id uuid NOT NULL REFERENCES records.source_document,
  version_id uuid NOT NULL REFERENCES records.original_version,
  source_url text NOT NULL, source_identifier text, family text NOT NULL,
  observed_at timestamptz NOT NULL, current_source boolean NOT NULL
);
CREATE INDEX versions_by_checksum ON records.original_version(sha256);
CREATE INDEX contexts_by_meeting ON records.document_context(meeting_id,item_id);
CREATE INDEX observations_by_url ON records.source_observation(source_url);
