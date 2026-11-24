CREATE TABLE analysis.analysis_operation (
  id uuid PRIMARY KEY,
  state text NOT NULL CHECK (state IN ('ACCEPTED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
  input_json text NOT NULL CHECK (jsonb_typeof(input_json::jsonb) = 'object'),
  command_sha256 varchar(64) NOT NULL CHECK (command_sha256 ~ '^[a-f0-9]{64}$'),
  accepted_at timestamptz NOT NULL, started_at timestamptz, completed_at timestamptz,
  attempt_id uuid, worker_id uuid, blocked_attempt_id uuid,
  error_code text, attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  revision_id uuid UNIQUE,
  CHECK ((state IN ('SUCCEEDED', 'FAILED')) = (completed_at IS NOT NULL)),
  CHECK ((state = 'FAILED') = (error_code IS NOT NULL)),
  CHECK ((state = 'SUCCEEDED') = (revision_id IS NOT NULL)),
  CHECK (blocked_attempt_id IS NULL OR state = 'RUNNING')
);
CREATE INDEX accepted_analysis_operations ON analysis.analysis_operation(accepted_at, id)
  WHERE state = 'ACCEPTED';
CREATE TABLE analysis.operation_slot (
  id integer PRIMARY KEY CHECK (id = 1),
  operation_id uuid UNIQUE REFERENCES analysis.analysis_operation,
  attempt_id uuid, worker_id uuid,
  CHECK ((operation_id IS NULL) = (attempt_id IS NULL)),
  CHECK ((operation_id IS NULL) = (worker_id IS NULL))
);
INSERT INTO analysis.operation_slot(id) VALUES (1);

CREATE TABLE analysis.meeting_analysis (
  id uuid PRIMARY KEY, operation_id uuid UNIQUE REFERENCES analysis.analysis_operation,
  origin text NOT NULL CHECK (origin IN ('OPERATION', 'IMPORTED')),
  meeting_id uuid NOT NULL, official_id text NOT NULL, meeting_date date NOT NULL, title text NOT NULL,
  contract_revision text NOT NULL, contract_detail_json text NOT NULL
    CHECK (jsonb_typeof(contract_detail_json::jsonb) = 'object'),
  profile_fingerprint varchar(64) NOT NULL CHECK (profile_fingerprint ~ '^[a-f0-9]{64}$'),
  input_sha256 varchar(64) NOT NULL CHECK (input_sha256 ~ '^[a-f0-9]{64}$'),
  model_input_sha256 varchar(64) NOT NULL CHECK (model_input_sha256 ~ '^[a-f0-9]{64}$'),
  candidates_sha256 varchar(64) NOT NULL CHECK (candidates_sha256 ~ '^[a-f0-9]{64}$'),
  receipt_sha256 varchar(64) NOT NULL CHECK (receipt_sha256 ~ '^[a-f0-9]{64}$'),
  prompt_tokens bigint NOT NULL CHECK (prompt_tokens >= 0),
  artifact_key text NOT NULL, created_at timestamptz NOT NULL,
  CHECK ((origin = 'OPERATION') = (operation_id IS NOT NULL))
);
ALTER TABLE analysis.analysis_operation ADD CONSTRAINT operation_revision
  FOREIGN KEY (revision_id) REFERENCES analysis.meeting_analysis;

CREATE TABLE analysis.analysis_issue (
  id uuid PRIMARY KEY, first_revision_id uuid NOT NULL REFERENCES analysis.meeting_analysis,
  first_label text NOT NULL
);
CREATE TABLE analysis.prior_context (
  id uuid PRIMARY KEY, revision_id uuid NOT NULL REFERENCES analysis.meeting_analysis,
  ordinal integer NOT NULL CHECK (ordinal >= 0), prior_label text NOT NULL,
  issue_id uuid NOT NULL REFERENCES analysis.analysis_issue,
  description text NOT NULL, reviewed_through date NOT NULL,
  snapshot_sha256 varchar(64) NOT NULL CHECK (snapshot_sha256 ~ '^[a-f0-9]{64}$'),
  UNIQUE (revision_id, prior_label), UNIQUE (revision_id, ordinal)
);
CREATE TABLE analysis.issue_mention (
  id uuid PRIMARY KEY, revision_id uuid NOT NULL REFERENCES analysis.meeting_analysis,
  ordinal integer NOT NULL CHECK (ordinal >= 0),
  issue_id uuid NOT NULL REFERENCES analysis.analysis_issue,
  local_label text NOT NULL, name text NOT NULL, prior_label text,
  FOREIGN KEY (revision_id, prior_label) REFERENCES analysis.prior_context(revision_id, prior_label),
  UNIQUE (revision_id, local_label), UNIQUE (revision_id, ordinal)
);
CREATE TABLE analysis.development (
  id uuid PRIMARY KEY, revision_id uuid NOT NULL REFERENCES analysis.meeting_analysis,
  local_label text NOT NULL, ordinal integer NOT NULL CHECK (ordinal >= 0),
  description text NOT NULL, status text NOT NULL CHECK (status IN ('PROPOSED', 'RECORDED', 'UNCERTAIN')),
  kind text NOT NULL CHECK (kind IN ('PROPOSAL', 'ACTION', 'EARLIER_ACTION', 'UNCLEAR')),
  UNIQUE (revision_id, local_label), UNIQUE (revision_id, ordinal), UNIQUE (revision_id, id)
);
CREATE TABLE analysis.development_date (
  id uuid PRIMARY KEY, development_id uuid NOT NULL REFERENCES analysis.development,
  ordinal integer NOT NULL CHECK (ordinal >= 0),
  role text NOT NULL CHECK (role IN ('OCCURRENCE', 'MEETING', 'PUBLICATION', 'PLANNED')),
  precision text NOT NULL CHECK (precision IN ('DAY', 'MONTH', 'YEAR', 'RANGE', 'UNKNOWN')),
  start text, "end" text, approximate boolean NOT NULL,
  CHECK (start IS NULL OR "end" IS NULL OR start <= "end"),
  UNIQUE (development_id, ordinal)
);
CREATE TABLE analysis.evidence_span (
  id uuid PRIMARY KEY, revision_id uuid NOT NULL REFERENCES analysis.meeting_analysis,
  local_label text NOT NULL, ordinal integer NOT NULL CHECK (ordinal >= 0),
  document_id uuid NOT NULL, version_id uuid NOT NULL, preparation_id uuid NOT NULL,
  profile_fingerprint varchar(64) NOT NULL CHECK (profile_fingerprint ~ '^[a-f0-9]{64}$'),
  segment_id uuid NOT NULL, start integer NOT NULL CHECK (start >= 0),
  "end" integer NOT NULL CHECK ("end" >= start), text text NOT NULL,
  unit_ordinal integer NOT NULL CHECK (unit_ordinal >= 0),
  segment_ordinal integer NOT NULL CHECK (segment_ordinal >= 0),
  locator_json text NOT NULL CHECK (jsonb_typeof(locator_json::jsonb) = 'object'),
  UNIQUE (revision_id, local_label), UNIQUE (revision_id, ordinal), UNIQUE (revision_id, id)
);
CREATE TABLE analysis.association (
  id uuid PRIMARY KEY, revision_id uuid NOT NULL REFERENCES analysis.meeting_analysis,
  ordinal integer NOT NULL CHECK (ordinal >= 0), development_id uuid NOT NULL,
  issue_id uuid NOT NULL REFERENCES analysis.analysis_issue,
  basis text NOT NULL CHECK (basis IN ('SOURCE_STATED', 'INFERRED')),
  FOREIGN KEY (revision_id, development_id) REFERENCES analysis.development(revision_id, id),
  UNIQUE (revision_id, ordinal), UNIQUE (revision_id, development_id, issue_id), UNIQUE (revision_id, id)
);
CREATE TABLE analysis.development_evidence (
  id uuid PRIMARY KEY, revision_id uuid NOT NULL REFERENCES analysis.meeting_analysis,
  development_id uuid NOT NULL, evidence_id uuid NOT NULL,
  ordinal integer NOT NULL CHECK (ordinal >= 0),
  FOREIGN KEY (revision_id, development_id) REFERENCES analysis.development(revision_id, id),
  FOREIGN KEY (revision_id, evidence_id) REFERENCES analysis.evidence_span(revision_id, id),
  UNIQUE (development_id, ordinal), UNIQUE (development_id, evidence_id)
);
CREATE TABLE analysis.association_evidence (
  id uuid PRIMARY KEY, revision_id uuid NOT NULL REFERENCES analysis.meeting_analysis,
  association_id uuid NOT NULL, evidence_id uuid NOT NULL,
  ordinal integer NOT NULL CHECK (ordinal >= 0),
  FOREIGN KEY (revision_id, association_id) REFERENCES analysis.association(revision_id, id),
  FOREIGN KEY (revision_id, evidence_id) REFERENCES analysis.evidence_span(revision_id, id),
  UNIQUE (association_id, ordinal), UNIQUE (association_id, evidence_id)
);
