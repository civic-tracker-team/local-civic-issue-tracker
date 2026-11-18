-- Sealed prepared revisions are evidence for later reads and analysis (ADR-0036). These triggers
-- make the database refuse to change them, whichever role or code path tries. Unsealed revisions
-- stay writable so that an interrupted preparation can resume and seal them.
-- Unit and segment checks lock the owning revision row FOR SHARE, so such a write cannot commit
-- alongside a concurrent seal of the same revision.

CREATE FUNCTION records.refuse_sealed_content_change() RETURNS trigger
  LANGUAGE plpgsql AS $fn$
BEGIN
  IF OLD.sealed_at IS NOT NULL THEN
    RAISE EXCEPTION 'prepared revision % is sealed', OLD.id USING ERRCODE = 'restrict_violation';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END
$fn$;

CREATE FUNCTION records.refuse_if_sealed(revision uuid) RETURNS void
  LANGUAGE plpgsql AS $fn$
DECLARE
  sealed timestamptz;
BEGIN
  -- Lock whether or not it is sealed yet: a concurrent seal then waits for this write, or this
  -- write waits for the seal and sees it.
  SELECT c.sealed_at INTO sealed FROM records.prepared_content c WHERE c.id = revision FOR SHARE;
  IF sealed IS NOT NULL THEN
    RAISE EXCEPTION 'prepared revision % is sealed', revision USING ERRCODE = 'restrict_violation';
  END IF;
END
$fn$;

CREATE FUNCTION records.refuse_sealed_unit_change() RETURNS trigger
  LANGUAGE plpgsql AS $fn$
BEGIN
  IF TG_OP <> 'INSERT' THEN
    PERFORM records.refuse_if_sealed(OLD.preparation_id);
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  PERFORM records.refuse_if_sealed(NEW.preparation_id);
  RETURN NEW;
END
$fn$;

CREATE FUNCTION records.refuse_sealed_segment_change() RETURNS trigger
  LANGUAGE plpgsql AS $fn$
BEGIN
  IF TG_OP <> 'INSERT' THEN
    PERFORM records.refuse_if_sealed(
      (SELECT u.preparation_id FROM records.prepared_unit u WHERE u.id = OLD.unit_id));
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  PERFORM records.refuse_if_sealed(
    (SELECT u.preparation_id FROM records.prepared_unit u WHERE u.id = NEW.unit_id));
  RETURN NEW;
END
$fn$;

CREATE TRIGGER sealed_content_immutable
  BEFORE UPDATE OR DELETE ON records.prepared_content
  FOR EACH ROW EXECUTE FUNCTION records.refuse_sealed_content_change();
CREATE TRIGGER sealed_unit_immutable
  BEFORE INSERT OR UPDATE OR DELETE ON records.prepared_unit
  FOR EACH ROW EXECUTE FUNCTION records.refuse_sealed_unit_change();
CREATE TRIGGER sealed_segment_immutable
  BEFORE INSERT OR UPDATE OR DELETE ON records.prepared_segment
  FOR EACH ROW EXECUTE FUNCTION records.refuse_sealed_segment_change();
