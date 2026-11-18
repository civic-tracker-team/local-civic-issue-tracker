package org.civictracker.analysis.domain;

/** A validation result at a specific reply or candidate location. */
public record Problem(Code code, String at) {
  public enum Code {
    MALFORMED_REPLY,
    LINE_OUT_OF_RANGE,
    REVERSED_RANGE,
    RANGE_CROSSES_UNITS,
    BLANK_RANGE,
    MISSING_EVIDENCE,
    DUPLICATE_ID,
    UNKNOWN_ISSUE,
    DUPLICATE_ASSOCIATION,
    UNKNOWN_PRIOR_ISSUE,
    INVALID_CALENDAR_DATE,
    REVERSED_DATE_RANGE,
    OCCURRENCE_WITHOUT_RECORD,
    OCCURRENCE_AFTER_MEETING,
    MEETING_DATE_MISMATCH,
    EVIDENCE_TEXT_MISMATCH,
    UNRESOLVED_EVIDENCE_REFERENCE,
    EMPTY_TEXT,
    LINKS_MERGED(false),
    UNUSED_ISSUE(false);

    private final boolean invalidates;

    Code() {
      this(true);
    }

    Code(boolean invalidates) {
      this.invalidates = invalidates;
    }

    public boolean invalidates() {
      return invalidates;
    }
  }
}
