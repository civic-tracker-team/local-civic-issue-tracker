-- Preserve Records' ordinal order independently of acquisition item UUIDs.
-- The preceding checkpoint had no run writer, so no legacy order is inferred.
ALTER TABLE coordinator.run_input
  ADD COLUMN input_order integer NOT NULL CHECK (input_order >= 0),
  ADD CONSTRAINT run_input_order UNIQUE (run_id, ordinal, input_order);
