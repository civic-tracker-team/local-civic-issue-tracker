-- One immutable index per published collection. Rows are written in one transaction and the
-- current pointer moves only after the whole collection has been indexed.
CREATE TABLE search.index_build (
  id uuid PRIMARY KEY,
  publication_id uuid NOT NULL UNIQUE,
  coverage_json text NOT NULL CHECK (jsonb_typeof(coverage_json::jsonb) = 'array'),
  record_types_json text NOT NULL CHECK (jsonb_typeof(record_types_json::jsonb) = 'array'),
  meeting_count integer NOT NULL CHECK (meeting_count >= 0),
  untexted_versions integer NOT NULL CHECK (untexted_versions >= 0),
  built_at timestamptz NOT NULL
);

CREATE TABLE search.current_index (
  id integer PRIMARY KEY CHECK (id = 1),
  build_id uuid REFERENCES search.index_build
);
INSERT INTO search.current_index(id) VALUES (1);

-- A searchable row. Issues and meetings have one row (part 0). A record has a metadata row
-- (part 0) and one row per prepared content unit (part = unit ordinal + 1); results are grouped
-- by target so one record appears once, with the snippet from its best-matching part. Every
-- part repeats its target's label and context, so a query can match a record's title together
-- with words on one of its pages. Weights: label A, context B, body C.
CREATE TABLE search.entry (
  id uuid PRIMARY KEY,
  build_id uuid NOT NULL REFERENCES search.index_build ON DELETE CASCADE,
  kind text NOT NULL CHECK (kind IN ('ISSUE', 'RECORD', 'MEETING')),
  target_id uuid NOT NULL,
  version_id uuid,
  part integer NOT NULL CHECK (part >= 0),
  label text NOT NULL,
  context text NOT NULL,
  body text NOT NULL,
  location text,
  details_json text NOT NULL CHECK (jsonb_typeof(details_json::jsonb) = 'object'),
  sort_date date,
  document tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('english', label), 'A')
      || setweight(to_tsvector('english', context), 'B')
      || setweight(to_tsvector('english', body), 'C')
  ) STORED,
  CHECK ((kind = 'RECORD') = (version_id IS NOT NULL)),
  CHECK (kind = 'RECORD' OR part = 0),
  UNIQUE NULLS NOT DISTINCT (build_id, kind, target_id, version_id, part)
);
CREATE INDEX entry_document ON search.entry USING gin (document);
CREATE INDEX entry_build_kind ON search.entry (build_id, kind);
