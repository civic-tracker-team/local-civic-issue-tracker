package org.civictracker.analysis.application;

/** Keeps deterministic save failures separate from model-runtime failures. */
public final class SaveRejected extends IllegalArgumentException {
  public enum Code {
    FINDING_KIND_MAPPING_MISMATCH,
    CONFLICTING_LINK_BASIS,
    EVIDENCE_SOURCE_MISMATCH,
    ATTEMPT_INPUT_CHANGED,
    ARTIFACT_HASH_MISMATCH,
    PRIOR_CONTEXT_INPUT_MISMATCH,
    UNKNOWN_EVIDENCE_SEGMENT,
    DUPLICATE_INPUT_SEGMENT,
    INVALID_LOCAL_LABEL,
    UNKNOWN_LOCAL_LABEL,
    TRIAL_NOT_SUCCESSFUL,
    WRONG_CONTRACT_REVISION,
    MISSING_RAW_REPLY,
    REPLY_NOT_ACCEPTED,
    CANDIDATES_DIFFER_FROM_REPLY,
    MISSING_TRIAL_METADATA
  }

  private final Code code;

  public SaveRejected(Code code) {
    this(code, code.name());
  }

  public SaveRejected(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
