-- Coordinator-owned run facts and immutable publications.
-- Every owner operation ID is derived (RunPlan.operationId), so no table stores "submitted" state.
-- Writers: Runs is the only writer of every table. Publications only reads.

CREATE TABLE coordinator.publication (
  id uuid PRIMARY KEY,                               -- RunPlan.publicationId(run_id)
  run_id uuid NOT NULL UNIQUE,                       -- FK added after coordinator.run exists
  previous_id uuid REFERENCES coordinator.publication,
  published_at timestamptz NOT NULL
);
CREATE TABLE coordinator.publication_range (
  publication_id uuid NOT NULL REFERENCES coordinator.publication,
  range_from date NOT NULL, range_to date NOT NULL CHECK (range_to >= range_from),
  PRIMARY KEY (publication_id, range_from)
);
CREATE TABLE coordinator.publication_meeting (
  publication_id uuid NOT NULL REFERENCES coordinator.publication,
  meeting_id uuid NOT NULL,
  official_id text NOT NULL, meeting_date date NOT NULL, title text NOT NULL,
  disposition text NOT NULL CHECK (disposition IN ('ANALYZED','CANCELLED','NO_RETAINED_ORIGINALS','SKIPPED')),
  revision_id uuid,
  skip_reason text,
  PRIMARY KEY (publication_id, meeting_id),
  CHECK ((disposition = 'ANALYZED') = (revision_id IS NOT NULL)),
  CHECK ((disposition = 'SKIPPED') = (skip_reason IS NOT NULL))
);
CREATE UNIQUE INDEX publication_revision ON coordinator.publication_meeting (publication_id, revision_id)
  WHERE revision_id IS NOT NULL;                     -- membership check: "is revision R in publication P"
CREATE TABLE coordinator.publication_source (
  publication_id uuid NOT NULL, meeting_id uuid NOT NULL,
  document_id uuid NOT NULL, version_id uuid NOT NULL,
  PRIMARY KEY (publication_id, meeting_id, version_id),
  FOREIGN KEY (publication_id, meeting_id) REFERENCES coordinator.publication_meeting
);
CREATE INDEX publication_source_version ON coordinator.publication_source(publication_id, version_id);

CREATE TABLE coordinator.current_publication (
  id integer PRIMARY KEY CHECK (id = 1),
  publication_id uuid REFERENCES coordinator.publication   -- NULL before the first publication
);
INSERT INTO coordinator.current_publication(id) VALUES (1);

CREATE TABLE coordinator.run (
  id uuid PRIMARY KEY,
  command_sha256 varchar(64) NOT NULL CHECK (command_sha256 ~ '^[a-f0-9]{64}$'),
  source_id text NOT NULL, range_from date NOT NULL, range_to date NOT NULL CHECK (range_to >= range_from),
  committee_ids text NOT NULL CHECK (jsonb_typeof(committee_ids::jsonb) = 'array'),
  max_meetings integer NOT NULL CHECK (max_meetings > 0),           -- config value frozen at admission
  preparation_profile_id text NOT NULL,                            -- config value frozen at admission
  base_publication_id uuid REFERENCES coordinator.publication,     -- current pointer at admission
  state text NOT NULL CHECK (state IN ('ACTIVE','HALTED','READY','PUBLISHED','ABANDONED')),
  discovery_attempt integer NOT NULL DEFAULT 1 CHECK (discovery_attempt > 0),
  discovered boolean NOT NULL DEFAULT false,
  halt_id integer NOT NULL DEFAULT 0,                              -- increments on every halt; retry/skip name it
  halt_kind text CHECK (halt_kind IN ('DISCOVER','ACQUIRE','PREPARE','ANALYZE','PUBLISH')), halt_ordinal integer, halt_operation_id uuid, halt_code text, -- Includes UNDATED_MEETING and owner-defined failure codes.
  halted_at timestamptz,
  observed_operation_id uuid, observed_state text CHECK (observed_state IN ('ACCEPTED','RUNNING','SUCCEEDED','FAILED','UNREACHABLE')), observed_blocked_id uuid, observed_at timestamptz,
  accepted_by text NOT NULL, accepted_at timestamptz NOT NULL, completed_at timestamptz,
  abandon_reason text,
  version bigint NOT NULL,                                         -- JPA @Version: tick vs maintainer
  CHECK ((state = 'HALTED') = (halt_code IS NOT NULL)),
  CHECK ((state = 'ABANDONED') = (abandon_reason IS NOT NULL)),
  CHECK ((state IN ('PUBLISHED','ABANDONED')) = (completed_at IS NOT NULL))
);
ALTER TABLE coordinator.publication ADD FOREIGN KEY (run_id) REFERENCES coordinator.run;

CREATE TABLE coordinator.run_slot (
  id integer PRIMARY KEY CHECK (id = 1),
  run_id uuid UNIQUE REFERENCES coordinator.run   -- ACTIVE, HALTED or READY run; NULL when free
);
INSERT INTO coordinator.run_slot(id) VALUES (1);

CREATE TABLE coordinator.run_meeting (
  run_id uuid NOT NULL REFERENCES coordinator.run,
  ordinal integer NOT NULL CHECK (ordinal >= 0),
  meeting_id uuid NOT NULL, official_id text NOT NULL, meeting_date date NOT NULL, title text NOT NULL,
  cancellation_evidence boolean NOT NULL,
  progress text NOT NULL CHECK (progress IN
    ('DISCOVERED','ACQUIRED','PREPARED','ANALYZED','NO_INPUTS','SKIPPED')),
  no_input_reason text CHECK (no_input_reason IN ('CANCELLED','NO_RETAINED_ORIGINALS')),
  acquire_attempt integer NOT NULL DEFAULT 1 CHECK (acquire_attempt > 0),
  prepare_attempt integer NOT NULL DEFAULT 1 CHECK (prepare_attempt > 0),
  analyze_attempt integer NOT NULL DEFAULT 1 CHECK (analyze_attempt > 0),
  frozen_request_json text CHECK (jsonb_typeof(frozen_request_json::jsonb) = 'object'),
  frozen_request_sha256 varchar(64) CHECK (frozen_request_sha256 ~ '^[a-f0-9]{64}$'),
  revision_id uuid,
  skip_reason text, skipped_by text, skipped_at timestamptz,
  PRIMARY KEY (run_id, ordinal),
  UNIQUE (run_id, meeting_id),
  CHECK ((frozen_request_json IS NULL) = (frozen_request_sha256 IS NULL)),
  CHECK ((progress = 'ANALYZED') = (revision_id IS NOT NULL)),
  CHECK ((progress = 'NO_INPUTS') = (no_input_reason IS NOT NULL)),
  CHECK ((progress = 'SKIPPED') = (skip_reason IS NOT NULL))
);

-- Pins are written once by the run writer when preparation succeeds.
CREATE TABLE coordinator.run_input (
  run_id uuid NOT NULL, ordinal integer NOT NULL,
  acquisition_item_id uuid NOT NULL, document_id uuid NOT NULL, version_id uuid NOT NULL,
  artifact_id uuid NOT NULL, sha256 varchar(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
  byte_length bigint NOT NULL CHECK (byte_length >= 0),
  preparation_id uuid NOT NULL, profile_fingerprint varchar(64) NOT NULL,
  PRIMARY KEY (run_id, ordinal, acquisition_item_id),
  FOREIGN KEY (run_id, ordinal) REFERENCES coordinator.run_meeting
);

-- Maintainer audit trail.
CREATE TABLE coordinator.run_event (
  id uuid PRIMARY KEY, run_id uuid NOT NULL REFERENCES coordinator.run,
  kind text NOT NULL CHECK (kind IN ('STARTED','HALTED','RETRIED','SKIPPED','ABANDONED','PUBLISHED')),
  halt_id integer, maintainer text, detail text, at timestamptz NOT NULL
);

